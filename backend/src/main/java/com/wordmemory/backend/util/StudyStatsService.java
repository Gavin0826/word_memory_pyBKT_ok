package com.wordmemory.backend.util;

import com.wordmemory.backend.entity.StudyRecord;
import com.wordmemory.backend.repository.StudyRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudyStatsService {

    @Autowired
    private StudyRecordRepository studyRecordRepository;

    public Map<String, Object> buildUserStats(Long userId) {
        Map<String, Object> result = new HashMap<>();
        List<StudyRecord> allRecords = studyRecordRepository.findByUserId(userId);

        if (allRecords.isEmpty()) {
            result.put("totalWords", 0L);
            result.put("masteredWords", 0L);
            result.put("totalRecords", 0L);
            result.put("correctCount", 0L);
            result.put("accuracy", 0);
            result.put("studiedDays", 0);
            result.put("consecutiveDays", 0);
            result.put("totalMinutes", 0L);
            result.put("categoryStats", new ArrayList<>());
            result.put("dailyStats", new ArrayList<>());
            return result;
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
                .collect(Collectors.groupingBy(r -> r.getStudyTime().toLocalDate().toString()));
        int studiedDays = byDay.size();

        int consecutiveDays = 0;
        LocalDate checkDate = LocalDate.now();
        while (byDay.containsKey(checkDate.toString())) {
            consecutiveDays++;
            checkDate = checkDate.minusDays(1);
        }
        if (consecutiveDays == 0) {
            checkDate = LocalDate.now().minusDays(1);
            while (byDay.containsKey(checkDate.toString())) {
                consecutiveDays++;
                checkDate = checkDate.minusDays(1);
            }
        }

        long totalMinutes = totalRecords * 30 / 60;

        Map<String, Long> catTotal = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getWord().getCategory(), Collectors.counting()));
        Map<String, Long> catCorrect = allRecords.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsCorrect()))
                .collect(Collectors.groupingBy(r -> r.getWord().getCategory(), Collectors.counting()));

        List<Map<String, Object>> categoryStats = new ArrayList<>();
        for (Map.Entry<String, Long> entry : catTotal.entrySet()) {
            String cat = entry.getKey();
            long ct = entry.getValue();
            long cc = catCorrect.getOrDefault(cat, 0L);
            long cw = allRecords.stream()
                    .filter(r -> cat.equals(r.getWord().getCategory()))
                    .map(r -> r.getWord().getId()).distinct().count();
            Map<String, Object> cs = new HashMap<>();
            cs.put("category", cat);
            cs.put("totalRecords", ct);
            cs.put("learnedWords", cw);
            cs.put("accuracy", ct > 0 ? (int) Math.round(cc * 100.0 / ct) : 0);
            categoryStats.add(cs);
        }
        categoryStats.sort((a, b) ->
                ((Long) b.get("learnedWords")).compareTo((Long) a.get("learnedWords")));

        List<Map<String, Object>> dailyStats = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 13; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.toString();
            List<StudyRecord> dayRecs = byDay.getOrDefault(dateStr, new ArrayList<>());
            long dayCorrect = dayRecs.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            long dayWords = dayRecs.stream()
                    .map(r -> r.getWord().getId()).distinct().count();
            Map<String, Object> ds = new HashMap<>();
            String lbl = i == 0 ? "今" : i == 1 ? "昨"
                    : date.getMonthValue() + "/" + date.getDayOfMonth();
            ds.put("date", dateStr);
            ds.put("dayLabel", lbl);
            ds.put("totalRecords", (long) dayRecs.size());
            ds.put("correctCount", dayCorrect);
            ds.put("learnedWords", dayWords);
            ds.put("accuracy", !dayRecs.isEmpty()
                    ? (int) Math.round(dayCorrect * 100.0 / dayRecs.size()) : 0);
            ds.put("hasStudy", !dayRecs.isEmpty());
            dailyStats.add(ds);
        }

        result.put("totalWords", totalWords);
        result.put("masteredWords", masteredWords);
        result.put("totalRecords", totalRecords);
        result.put("correctCount", correctCount);
        result.put("accuracy", accuracy);
        result.put("studiedDays", studiedDays);
        result.put("consecutiveDays", consecutiveDays);
        result.put("totalMinutes", totalMinutes);
        result.put("categoryStats", categoryStats);
        result.put("dailyStats", dailyStats);
        return result;
    }
}
