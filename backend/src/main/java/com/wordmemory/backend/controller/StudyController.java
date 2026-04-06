package com.wordmemory.backend.controller;

import com.wordmemory.backend.entity.StudyRecord;
import com.wordmemory.backend.entity.User;
import com.wordmemory.backend.entity.Word;
import com.wordmemory.backend.entity.WordMastery;
import com.wordmemory.backend.repository.StudyRecordRepository;
import com.wordmemory.backend.repository.UserRepository;
import com.wordmemory.backend.repository.WordMasteryRepository;
import com.wordmemory.backend.repository.WordRepository;
import com.wordmemory.backend.util.BktInferenceService;
import com.wordmemory.backend.util.Sm2Scheduler;
import com.wordmemory.backend.util.StudyStatsService;
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
    @Autowired
    private WordMasteryRepository wordMasteryRepository;
    @Autowired
    private BktInferenceService bktInferenceService;
    @Autowired
    private StudyStatsService studyStatsService;

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
     * 基于后端在线 BKT 推断结果映射 SM-2 质量分 q。
     *
     * 一致性约束：
     * - isCorrect=true 时，q 最低为 3（避免“答对却按失败重置”）
     * - isCorrect=false 时，q 最高为 2（避免“答错却给高分”）
     */
    private int resolveSm2Quality(Boolean isCorrect, Long userId, Long wordId, String difficulty) {
        int fallbackQ = Boolean.TRUE.equals(isCorrect) ? 5 : 2;
        Object raw = null;
        if (userId != null && wordId != null) {
            try {
                raw = bktInferenceService.inferMastery(userId, wordId, difficulty);
            } catch (Exception ignored) { }
        }
        if (raw == null && userId != null && wordId != null) {
            try {
                Optional<WordMastery> wm = wordMasteryRepository.findByUserIdAndWordId(userId, wordId);
                if (wm.isPresent()) raw = wm.get().getMasteryProbability();
            } catch (Exception ignored) { }
        }
        if (raw != null) {
            try {
                double p = Double.parseDouble(raw.toString());
                p = Math.max(0.0, Math.min(1.0, p));
                int mappedQ = mapBktProbabilityToQ(p);
                if (Boolean.TRUE.equals(isCorrect)) return Math.max(mappedQ, 3);
                return Math.min(mappedQ, 2);
            } catch (Exception ignored) { }
        }
        return fallbackQ;
    }

    private void updateWordMastery(Long userId, Long wordId, String difficulty) {
        try {
            double p;
            try {
                p = bktInferenceService.inferMastery(userId, wordId, difficulty);
            } catch (Exception e) {
                p = wordMasteryRepository.findByUserIdAndWordId(userId, wordId)
                        .map(WordMastery::getMasteryProbability).orElse(0.5);
            }
            p = Math.max(0.0, Math.min(1.0, p));
            p = Math.round(p * 10000.0) / 10000.0;
            wordMasteryRepository.upsertMastery(userId, wordId, p, "v1.0");
        } catch (Exception ignored) { }
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

            String difficulty = (word.getDifficulty() != null) ? word.getDifficulty() : "medium";
            int q = resolveSm2Quality(isCorrect, userId, wordId, difficulty);
            Sm2Scheduler.Result sm2 = Sm2Scheduler.next(prevEF, prevRepetition, prevIntervalDays, q);

            int prevConsecutive = (latest != null && latest.getConsecutiveCorrect() != null) ? latest.getConsecutiveCorrect() : 0;
            record.setConsecutiveCorrect(isCorrect ? prevConsecutive + 1 : 0);
            record.setEaseFactor(sm2.easeFactor);
            record.setRepetition(sm2.repetition);
            record.setIntervalDays(sm2.intervalDays);
            record.setReviewStage(sm2.repetition);
            record.setNextReviewTime(LocalDateTime.now().plusDays(sm2.intervalDays));
            record.setQScore(q);

            studyRecordRepository.save(record);
            updateWordMastery(userId, wordId, difficulty);
            if (isCorrect && "new".equals(studyType)) {
                user.setTotalWords(user.getTotalWords() + 1);
                userRepository.save(user);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success"); response.put("recordId", record.getId());
            response.put("nextReviewTime", record.getNextReviewTime());
            response.put("reviewStage", record.getReviewStage());
            response.put("consecutiveCorrect", record.getConsecutiveCorrect());
            response.put("qScore", q);
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

            String difficulty = (word.getDifficulty() != null) ? word.getDifficulty() : "medium";
            int q = resolveSm2Quality(isCorrect, userId, wordId, difficulty);
            Sm2Scheduler.Result sm2 = Sm2Scheduler.next(prevEF, prevRepetition, prevIntervalDays, q);

            record.setConsecutiveCorrect(isCorrect ? prevConsecutive + 1 : 0);
            record.setEaseFactor(sm2.easeFactor);
            record.setRepetition(sm2.repetition);
            record.setIntervalDays(sm2.intervalDays);
            record.setReviewStage(sm2.repetition);
            record.setNextReviewTime(LocalDateTime.now().plusDays(sm2.intervalDays));
            record.setQScore(q);

            studyRecordRepository.save(record);
            updateWordMastery(userId, wordId, difficulty);

            response.put("status", "success"); response.put("message", "saved");
            response.put("nextReviewTime", record.getNextReviewTime());
            response.put("consecutiveCorrect", record.getConsecutiveCorrect());
            response.put("reviewStage", record.getReviewStage());
            response.put("qScore", q);
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

    // 返回用户所有错题记录（历史全量）
    @GetMapping("/{userId}/wrong-words")
    public List<StudyRecord> getWrongWords(@PathVariable Long userId) {
        List<StudyRecord> records = studyRecordRepository.findByUserIdAndIsCorrectFalse(userId);
        records.sort(Comparator.comparing(StudyRecord::getStudyTime).reversed());
        return records;
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
            Map<String, Object> stats = studyStatsService.buildUserStats(userId);
            response.put("status", "success");
            response.putAll(stats);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "stats error: " + e.getMessage());
        }
        return response;
    }

    /**
     * 获取用户所有单词的掌握概率列表（用于前端展示）
     * GET /api/study/{userId}/mastery
     */
    @GetMapping("/{userId}/mastery")
    public Map<String, Object> getUserMastery(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<WordMastery> masteryList = wordMasteryRepository.findAll().stream()
                    .filter(m -> m.getUserId().equals(userId))
                    .collect(Collectors.toList());
            List<Map<String, Object>> result = new ArrayList<>();
            for (WordMastery wm : masteryList) {
                Map<String, Object> item = new HashMap<>();
                item.put("wordId", wm.getWordId());
                item.put("masteryProbability", wm.getMasteryProbability());
                item.put("modelVersion", wm.getModelVersion());
                item.put("updatedAt", wm.getUpdatedAt());
                // 根据概率给出掌握等级标签
                double p = wm.getMasteryProbability();
                String level;
                if (p >= 0.85) level = "熟练掌握";
                else if (p >= 0.70) level = "基本掌握";
                else if (p >= 0.50) level = "初步了解";
                else level = "需要加强";
                item.put("masteryLevel", level);
                // 查单词信息
                wordRepository.findById(wm.getWordId()).ifPresent(w -> {
                    item.put("word", w.getWord());
                    item.put("meaning", w.getMeaning());
                    item.put("category", w.getCategory());
                    item.put("difficulty", w.getDifficulty());
                });
                result.add(item);
            }
            // 按概率从低到高排序（优先展示需要加强的）
            result.sort((a, b) ->
                    Double.compare((Double) a.get("masteryProbability"),
                                   (Double) b.get("masteryProbability")));
            response.put("status", "success");
            response.put("masteryList", result);
            response.put("totalWords", result.size());
            double avgP = result.stream()
                    .mapToDouble(m -> (Double) m.get("masteryProbability")).average().orElse(0);
            response.put("averageMastery", Math.round(avgP * 10000.0) / 10000.0);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }
}
