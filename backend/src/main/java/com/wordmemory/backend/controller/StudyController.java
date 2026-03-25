package com.wordmemory.backend.controller;

import com.wordmemory.backend.entity.StudyRecord;
import com.wordmemory.backend.entity.User;
import com.wordmemory.backend.entity.Word;
import com.wordmemory.backend.repository.StudyRecordRepository;
import com.wordmemory.backend.repository.UserRepository;
import com.wordmemory.backend.repository.WordRepository;
import com.wordmemory.backend.util.Sm2Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/study")
public class StudyController {

    @Autowired
    private StudyRecordRepository studyRecordRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WordRepository wordRepository;

    /**
     * 将 pyBKT 概率映射为 SM-2 质量分 q：
     * p>=0.85 -> 5, [0.70,0.85) -> 4, [0.50,0.70) -> 3, <0.50 -> 2
     */
    private int mapBktProbabilityToQ(double p) {
        if (p >= 0.85) return 5;
        if (p >= 0.70) return 4;
        if (p >= 0.50) return 3;
        return 2;
    }

    /**
     * 兼容接入：优先使用请求中的 bktProbability/masteryProbability 映射 q；
     * 若未提供或非法，则回退到原逻辑（正确=5，错误=2）。
     *
     * 一致性约束：
     * - isCorrect=true 时，q 最低为 3（避免“答对却按失败重置”）
     * - isCorrect=false 时，q 最高为 2（避免“答错却给高分”）
     */
    private int resolveSm2Quality(Boolean isCorrect, Map<String, Object> request) {
        Object raw = request.get("bktProbability");
        if (raw == null) raw = request.get("masteryProbability");

        int fallbackQ = Boolean.TRUE.equals(isCorrect) ? 5 : 2;

        if (raw != null) {
            try {
                double p = Double.parseDouble(raw.toString());
                p = Math.max(0.0, Math.min(1.0, p));
                int mappedQ = mapBktProbabilityToQ(p);

                if (Boolean.TRUE.equals(isCorrect)) {
                    return Math.max(mappedQ, 3);
                }
                return Math.min(mappedQ, 2);
            } catch (Exception ignored) {
                // ignore and fallback
            }
        }

        return fallbackQ;
    }

    // Bug2 fixed: always creates a new record, never mutates existing ones
    @PostMapping("/record")
    public Map<String, Object> recordStudy(@RequestBody Map<String, Object> request) {
        if (request == null || !request.containsKey("userId") || !request.containsKey("wordId")
                || !request.containsKey("isCorrect") || !request.containsKey("studyType")) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error"); error.put("message", "incomplete"); return error;
        }
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long wordId = Long.valueOf(request.get("wordId").toString());
            Boolean isCorrect = Boolean.valueOf(request.get("isCorrect").toString());
            String studyType = request.get("studyType").toString();
            User user = userRepository.findById(userId).orElse(null);
            Word word = wordRepository.findById(wordId).orElse(null);
            if (user == null || word == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "error"); err.put("message", "user or word not found"); return err;
            }
            StudyRecord record = new StudyRecord();
            record.setUser(user); record.setWord(word);
            record.setIsCorrect(isCorrect); record.setStudyType(studyType);
            record.setStudyTime(LocalDateTime.now());

            // ==================== SM-2 调度计算 ====================
            StudyRecord latest = studyRecordRepository.findTopByUserIdAndWordIdOrderByStudyTimeDesc(userId, wordId);
            double prevEF = (latest != null && latest.getEaseFactor() != null) ? latest.getEaseFactor() : 2.5;
            int prevRepetition = (latest != null && latest.getRepetition() != null) ? latest.getRepetition() : 0;
            int prevIntervalDays = (latest != null && latest.getIntervalDays() != null) ? latest.getIntervalDays() : 0;

            // 质量评分映射：优先使用 pyBKT 概率映射；未提供则回退（正确=5，错误=2）
            int q = resolveSm2Quality(isCorrect, request);
            Sm2Scheduler.Result sm2 = Sm2Scheduler.next(prevEF, prevRepetition, prevIntervalDays, q);

            // 连续答对次数（用于 UI/统计兼容）
            int prevConsecutive = (latest != null && latest.getConsecutiveCorrect() != null) ? latest.getConsecutiveCorrect() : 0;
            record.setConsecutiveCorrect(isCorrect ? prevConsecutive + 1 : 0);

            // 写入 SM-2 状态
            record.setEaseFactor(sm2.easeFactor);
            record.setRepetition(sm2.repetition);
            record.setIntervalDays(sm2.intervalDays);

            // reviewStage 兼容展示：用 repetition 表示
            record.setReviewStage(sm2.repetition);

            record.setNextReviewTime(LocalDateTime.now().plusDays(sm2.intervalDays));

            studyRecordRepository.save(record);
            if (isCorrect && "new".equals(studyType)) {
                user.setTotalWords(user.getTotalWords() + 1);
                userRepository.save(user);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success"); response.put("recordId", record.getId());
            response.put("nextReviewTime", record.getNextReviewTime());
            response.put("reviewStage", record.getReviewStage());
            response.put("consecutiveCorrect", record.getConsecutiveCorrect());
            response.put("message", "saved"); return response;
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "error"); err.put("message", e.getMessage()); return err;
        }
    }

    // Bug2 fixed: creates NEW record each time to preserve full history
    @PostMapping("/record-custom")
    public Map<String, Object> recordCustom(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        if (request == null || !request.containsKey("userId")
                || !request.containsKey("wordId") || !request.containsKey("actionType")) {
            response.put("status", "error"); response.put("message", "incomplete"); return response;
        }
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long wordId = Long.valueOf(request.get("wordId").toString());
            String actionType = request.get("actionType").toString();
            User user = userRepository.findById(userId).orElse(null);
            Word word = wordRepository.findById(wordId).orElse(null);
            if (user == null || word == null) {
                response.put("status", "error"); response.put("message", "not found"); return response;
            }

            // Read latest record to get SM-2 state, but always create a NEW record
            StudyRecord latestRecord = studyRecordRepository
                    .findTopByUserIdAndWordIdOrderByStudyTimeDesc(userId, wordId);
            boolean isFirstTime = (latestRecord == null);

            double prevEF = (latestRecord != null && latestRecord.getEaseFactor() != null)
                    ? latestRecord.getEaseFactor() : 2.5;
            int prevRepetition = (latestRecord != null && latestRecord.getRepetition() != null)
                    ? latestRecord.getRepetition() : 0;
            int prevIntervalDays = (latestRecord != null && latestRecord.getIntervalDays() != null)
                    ? latestRecord.getIntervalDays() : 0;
            int prevConsecutive = (latestRecord != null && latestRecord.getConsecutiveCorrect() != null)
                    ? latestRecord.getConsecutiveCorrect() : 0;

            StudyRecord record = new StudyRecord();
            record.setUser(user); record.setWord(word);
            record.setStudyTime(LocalDateTime.now());
            record.setStudyType(isFirstTime ? "new" : "review");

            boolean isCorrect;
            if ("know".equals(actionType) || "test_correct".equals(actionType)) isCorrect = true;
            else if ("unknown".equals(actionType) || "test_wrong".equals(actionType)) isCorrect = false;
            else {
                response.put("status", "error"); response.put("message", "invalid actionType");
                return response;
            }

            record.setIsCorrect(isCorrect);

            int q = resolveSm2Quality(isCorrect, request);
            Sm2Scheduler.Result sm2 = Sm2Scheduler.next(prevEF, prevRepetition, prevIntervalDays, q);

            record.setConsecutiveCorrect(isCorrect ? prevConsecutive + 1 : 0);
            record.setEaseFactor(sm2.easeFactor);
            record.setRepetition(sm2.repetition);
            record.setIntervalDays(sm2.intervalDays);
            record.setReviewStage(sm2.repetition);
            record.setNextReviewTime(LocalDateTime.now().plusDays(sm2.intervalDays));

            studyRecordRepository.save(record);
            response.put("status", "success"); response.put("message", "saved");
            response.put("nextReviewTime", record.getNextReviewTime());
            response.put("consecutiveCorrect", record.getConsecutiveCorrect());
            response.put("reviewStage", record.getReviewStage());
        } catch (Exception e) {
            response.put("status", "error"); response.put("message", e.getMessage());
        }
        return response;
    }

    // Bug1 fix: delete wrong-answer records for a specific word
    @DeleteMapping("/{userId}/wrong-words/{wordId}")
    public Map<String, Object> deleteWrongWordRecords(
            @PathVariable Long userId, @PathVariable Long wordId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<StudyRecord> records = studyRecordRepository.findByUserId(userId).stream()
                    .filter(r -> r.getWord().getId().equals(wordId)
                            && Boolean.FALSE.equals(r.getIsCorrect()))
                    .collect(Collectors.toList());
            studyRecordRepository.deleteAll(records);
            response.put("status", "success"); response.put("deleted", records.size());
        } catch (Exception e) {
            response.put("status", "error"); response.put("message", e.getMessage());
        }
        return response;
    }

    // Bug1 fix: clear all wrong-answer records for a user
    @DeleteMapping("/{userId}/wrong-words")
    public Map<String, Object> clearAllWrongWords(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<StudyRecord> records = studyRecordRepository.findByUserIdAndIsCorrectFalse(userId);
            studyRecordRepository.deleteAll(records);
            response.put("status", "success"); response.put("deleted", records.size());
        } catch (Exception e) {
            response.put("status", "error"); response.put("message", e.getMessage());
        }
        return response;
    }

    @GetMapping("/category-task")
    public Map<String, Object> getCategoryTask(
            @RequestParam Long userId, @RequestParam String category,
            @RequestParam(defaultValue = "10") int newWordsCount) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Word> allCatWords = wordRepository.findByCategory(category);
            // 修复1：只有「最新记录答对」的词才算真正学过，答错/未测试的词仍放入新词列表
            List<StudyRecord> allRecords = studyRecordRepository.findByUserId(userId);
            Map<Long, StudyRecord> latestPerWord = new HashMap<>();
            for (StudyRecord r : allRecords) {
                if (r.getWord() == null || r.getWord().getId() == null) continue;
                Long wid = r.getWord().getId();
                StudyRecord cur = latestPerWord.get(wid);
                if (cur == null || (r.getStudyTime() != null && cur.getStudyTime() != null
                        && r.getStudyTime().isAfter(cur.getStudyTime()))) {
                    latestPerWord.put(wid, r);
                }
            }
            // 最新记录答对 = 已掌握，不再出现在新词
            Set<Long> mastered = latestPerWord.entrySet().stream()
                    .filter(e -> Boolean.TRUE.equals(e.getValue().getIsCorrect()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            List<Word> newWords = allCatWords.stream()
                    .filter(w -> !mastered.contains(w.getId()))
                    .limit(newWordsCount).collect(Collectors.toList());
            response.put("status", "success");
            response.put("reviewWords", new ArrayList<>());
            response.put("reviewCount", 0);
            response.put("newWords", newWords);
            response.put("newCount", newWords.size());
            response.put("totalWords", newWords.size());
        } catch (Exception e) {
            response.put("status", "error"); response.put("message", e.getMessage());
        }
        return response;
    }

    @GetMapping("/{userId}/review-words")
    public List<StudyRecord> getReviewWords(@PathVariable Long userId) {
        // SM-2 调度依赖“最新一条记录”状态，因此这里必须只返回每个单词的最新记录，并且其 nextReviewTime 已到期
        LocalDateTime now = LocalDateTime.now();
        List<StudyRecord> all = studyRecordRepository.findByUserId(userId);
        Map<Long, StudyRecord> latestPerWord = new HashMap<>();
        for (StudyRecord r : all) {
            if (r == null || r.getWord() == null || r.getWord().getId() == null) continue;
            Long wid = r.getWord().getId();
            StudyRecord cur = latestPerWord.get(wid);
            if (cur == null || (r.getStudyTime() != null && cur.getStudyTime() != null
                    && r.getStudyTime().compareTo(cur.getStudyTime()) > 0)) {
                latestPerWord.put(wid, r);
            }
        }
        return latestPerWord.values().stream()
                .filter(r -> r.getNextReviewTime() != null && r.getNextReviewTime().isBefore(now))
                .sorted(Comparator.comparing(StudyRecord::getNextReviewTime))
                .collect(Collectors.toList());
    }

    // Bug棰濆 fixed: only return words whose MOST RECENT record is incorrect
    @GetMapping("/{userId}/wrong-words")
    public List<StudyRecord> getWrongWords(@PathVariable Long userId) {
        List<StudyRecord> all = studyRecordRepository.findByUserId(userId);
        Map<Long, StudyRecord> latestPerWord = new LinkedHashMap<>();
        all.stream()
                .sorted((a, b) -> a.getStudyTime().compareTo(b.getStudyTime()))
                .forEach(r -> latestPerWord.put(r.getWord().getId(), r));
        return latestPerWord.values().stream()
                .filter(r -> Boolean.FALSE.equals(r.getIsCorrect()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{userId}/total-records")
    public Map<String, Object> getTotalRecords(@PathVariable Long userId) {
        long total = studyRecordRepository.countByUserId(userId);
        Map<String, Object> r = new HashMap<>();
        r.put("userId", userId); r.put("totalRecords", total); return r;
    }

    @GetMapping("/{userId}/stats")
    public Map<String, Object> getUserStats(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<StudyRecord> allRecords = studyRecordRepository.findByUserId(userId);
            if (allRecords.isEmpty()) {
                response.put("status", "success"); response.put("totalWords", 0L);
                response.put("masteredWords", 0L); response.put("totalRecords", 0L);
                response.put("correctCount", 0L); response.put("accuracy", 0);
                response.put("studiedDays", 0); response.put("consecutiveDays", 0);
                response.put("totalMinutes", 0L);
                response.put("categoryStats", new ArrayList<>());
                response.put("dailyStats", new ArrayList<>()); return response;
            }
            long totalRecords = allRecords.size();
            long correctCount = allRecords.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            int accuracy = (int) Math.round(correctCount * 100.0 / totalRecords);
            long totalWords = allRecords.stream()
                    .map(r -> r.getWord().getId()).distinct().count();
            long masteredWords = allRecords.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsCorrect()))
                    .map(r -> r.getWord().getId()).distinct().count();
            Map<String, List<StudyRecord>> byDay = allRecords.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getStudyTime().toLocalDate().toString()));
            int studiedDays = byDay.size();
            int consecutiveDays = 0;
            LocalDate checkDate = LocalDate.now();
            while (byDay.containsKey(checkDate.toString())) {
                consecutiveDays++; checkDate = checkDate.minusDays(1);
            }
            if (consecutiveDays == 0) {
                checkDate = LocalDate.now().minusDays(1);
                while (byDay.containsKey(checkDate.toString())) {
                    consecutiveDays++; checkDate = checkDate.minusDays(1);
                }
            }
            long totalMinutes = totalRecords * 30 / 60;
            Map<String, Long> catTotal = allRecords.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getWord().getCategory(), Collectors.counting()));
            Map<String, Long> catCorrect = allRecords.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsCorrect()))
                    .collect(Collectors.groupingBy(
                            r -> r.getWord().getCategory(), Collectors.counting()));
            List<Map<String, Object>> categoryStats = new ArrayList<>();
            for (Map.Entry<String, Long> entry : catTotal.entrySet()) {
                String cat = entry.getKey(); long ct = entry.getValue();
                long cc = catCorrect.getOrDefault(cat, 0L);
                long cw = allRecords.stream()
                        .filter(r -> cat.equals(r.getWord().getCategory()))
                        .map(r -> r.getWord().getId()).distinct().count();
                Map<String, Object> cs = new HashMap<>();
                cs.put("category", cat); cs.put("totalRecords", ct);
                cs.put("learnedWords", cw);
                cs.put("accuracy", ct > 0 ? (int) Math.round(cc * 100.0 / ct) : 0);
                categoryStats.add(cs);
            }
            categoryStats.sort((a, b) ->
                    ((Long) b.get("learnedWords")).compareTo((Long) a.get("learnedWords")));
            List<Map<String, Object>> dailyStats = new ArrayList<>();
            LocalDate today = LocalDate.now();
            for (int i = 13; i >= 0; i--) {
                LocalDate date = today.minusDays(i); String dateStr = date.toString();
                List<StudyRecord> dayRecs = byDay.getOrDefault(dateStr, new ArrayList<>());
                long dayCorrect = dayRecs.stream()
                        .filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
                long dayWords = dayRecs.stream()
                        .map(r -> r.getWord().getId()).distinct().count();
                Map<String, Object> ds = new HashMap<>();
                String lbl = i == 0 ? "\u4eca" : i == 1 ? "\u6628"
                        : date.getMonthValue() + "/" + date.getDayOfMonth();
                ds.put("date", dateStr); ds.put("dayLabel", lbl);
                ds.put("totalRecords", (long) dayRecs.size());
                ds.put("correctCount", dayCorrect); ds.put("learnedWords", dayWords);
                ds.put("accuracy", !dayRecs.isEmpty()
                        ? (int) Math.round(dayCorrect * 100.0 / dayRecs.size()) : 0);
                ds.put("hasStudy", !dayRecs.isEmpty());
                dailyStats.add(ds);
            }
            response.put("status", "success"); response.put("totalWords", totalWords);
            response.put("masteredWords", masteredWords); response.put("totalRecords", totalRecords);
            response.put("correctCount", correctCount); response.put("accuracy", accuracy);
            response.put("studiedDays", studiedDays); response.put("consecutiveDays", consecutiveDays);
            response.put("totalMinutes", totalMinutes);
            response.put("categoryStats", categoryStats); response.put("dailyStats", dailyStats);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "stats error: " + e.getMessage());
        }
        return response;
    }
}
