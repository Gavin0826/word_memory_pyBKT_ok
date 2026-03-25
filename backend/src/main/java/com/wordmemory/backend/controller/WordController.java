package com.wordmemory.backend.controller;

import com.wordmemory.backend.entity.StudyRecord;
import com.wordmemory.backend.entity.Word;
import com.wordmemory.backend.repository.StudyRecordRepository;
import com.wordmemory.backend.repository.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/words")
public class WordController {

    @Autowired
    private WordRepository wordRepository;

    @Autowired
    private StudyRecordRepository studyRecordRepository;

    // 获取所有单词（分页）
    @GetMapping("/all")
    public List<Word> getAllWords() {
        return wordRepository.findAll();
    }

    // 按分类获取单词
    @GetMapping("/category/{category}")
    public List<Word> getByCategory(@PathVariable String category) {
        return wordRepository.findByCategory(category);
    }

    // 统计各分类单词数
    @GetMapping("/stats")
    public String getWordStats() {
        long cet4 = wordRepository.countByCategory("CET-4");
        long cet6 = wordRepository.countByCategory("CET-6");
        long total = wordRepository.count();

        return String.format(
                "{\"total\": %d, \"CET-4\": %d, \"CET-6\": %d}",
                total, cet4, cet6
        );
    }

    // ==================== 管理员专用接口 ====================

    /**
     * 获取所有词库分类及实时单词数量
     */
    @GetMapping("/categories")
    public Map<String, Object> getCategories() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Word> allWords = wordRepository.findAll();
            Map<String, Long> countMap = allWords.stream()
                    .collect(Collectors.groupingBy(Word::getCategory, Collectors.counting()));
            List<Map<String, Object>> categories = new ArrayList<>();
            String[] order = {"CET-4", "CET-6"};
            for (String cat : order) {
                if (countMap.containsKey(cat)) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", cat);
                    item.put("name", getCategoryDisplayName(cat));
                    item.put("count", countMap.get(cat));
                    item.put("desc", getCategoryDesc(cat));
                    categories.add(item);
                }
            }
            for (String cat : countMap.keySet()) {
                if (!Arrays.asList(order).contains(cat)) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", cat);
                    item.put("name", cat);
                    item.put("count", countMap.get(cat));
                    item.put("desc", cat + " 词库");
                    categories.add(item);
                }
            }
            response.put("status", "success");
            response.put("categories", categories);
            response.put("totalWords", (long) allWords.size());
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "获取词库列表失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 按分类获取单词列表（支持搜索和分页）
     */
    @GetMapping("/admin/list")
    public Map<String, Object> adminGetWords(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Word> words;
            if (category != null && !category.isEmpty()) {
                words = wordRepository.findByCategory(category);
            } else {
                words = wordRepository.findAll();
            }
            if (!keyword.isEmpty()) {
                String kw = keyword.toLowerCase();
                words = words.stream()
                        .filter(w -> w.getWord().toLowerCase().contains(kw)
                                || (w.getMeaning() != null && w.getMeaning().contains(kw)))
                        .collect(Collectors.toList());
            }
            int total = words.size();
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, total);
            List<Word> pageWords = start < total ? words.subList(start, end) : new ArrayList<>();
            response.put("status", "success");
            response.put("words", pageWords);
            response.put("total", total);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("totalPages", (int) Math.ceil((double) total / pageSize));
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "获取单词列表失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 添加单词
     */
    @PostMapping("/admin/add")
    public Map<String, Object> addWord(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String word = request.get("word");
            String pronunciation = request.getOrDefault("pronunciation", "");
            String meaning = request.get("meaning");
            String difficulty = request.getOrDefault("difficulty", "medium");
            String category = request.get("category");
            if (word == null || word.trim().isEmpty()) {
                response.put("status", "error"); response.put("message", "单词不能为空"); return response;
            }
            if (meaning == null || meaning.trim().isEmpty()) {
                response.put("status", "error"); response.put("message", "释义不能为空"); return response;
            }
            if (category == null || category.trim().isEmpty()) {
                response.put("status", "error"); response.put("message", "词库不能为空"); return response;
            }
            Word newWord = new Word();
            newWord.setWord(word.trim());
            newWord.setPronunciation(pronunciation.trim());
            newWord.setMeaning(meaning.trim());
            newWord.setDifficulty(difficulty);
            newWord.setCategory(category.trim());
            wordRepository.save(newWord);
            response.put("status", "success");
            response.put("message", "单词添加成功");
            response.put("word", newWord);
            response.put("categoryCount", wordRepository.countByCategory(category.trim()));
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "添加单词失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 修改单词
     */
    @PutMapping("/admin/update/{wordId}")
    public Map<String, Object> updateWord(@PathVariable Long wordId, @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Word word = wordRepository.findById(wordId).orElse(null);
            if (word == null) {
                response.put("status", "error"); response.put("message", "单词不存在"); return response;
            }
            if (request.containsKey("word") && !request.get("word").trim().isEmpty())
                word.setWord(request.get("word").trim());
            if (request.containsKey("pronunciation"))
                word.setPronunciation(request.get("pronunciation").trim());
            if (request.containsKey("meaning") && !request.get("meaning").trim().isEmpty())
                word.setMeaning(request.get("meaning").trim());
            if (request.containsKey("difficulty"))
                word.setDifficulty(request.get("difficulty"));
            if (request.containsKey("category") && !request.get("category").trim().isEmpty())
                word.setCategory(request.get("category").trim());
            wordRepository.save(word);
            response.put("status", "success");
            response.put("message", "单词修改成功");
            response.put("word", word);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "修改单词失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 删除单词（同时清理相关学习记录）
     */
    @DeleteMapping("/admin/delete/{wordId}")
    public Map<String, Object> deleteWord(@PathVariable Long wordId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Word word = wordRepository.findById(wordId).orElse(null);
            if (word == null) {
                response.put("status", "error"); response.put("message", "单词不存在"); return response;
            }
            String category = word.getCategory();
            List<StudyRecord> records = studyRecordRepository.findByWordId(wordId);
            if (!records.isEmpty()) studyRecordRepository.deleteAll(records);
            wordRepository.delete(word);
            response.put("status", "success");
            response.put("message", "单词删除成功");
            response.put("categoryCount", wordRepository.countByCategory(category));
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "删除单词失败: " + e.getMessage());
        }
        return response;
    }

    // ==================== 工具方法 ====================

    private String getCategoryDisplayName(String category) {
        switch (category) {
            case "CET-4": return "CET-4 四级词库";
            case "CET-6": return "CET-6 六级词库";
            default: return category;
        }
    }

    private String getCategoryDesc(String category) {
        switch (category) {
            case "CET-4": return "大学英语四级核心词汇";
            case "CET-6": return "大学英语六级核心词汇";
            default: return category + " 词库";
        }
    }

    // ==================== 原有接口保留 ====================

    /**
     * 获取今日学习任务（新词 + 复习词）- 新添加的方法
     */
    @GetMapping("/today-task")
    public Map<String, Object> getTodayTask(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "10") int newWordsCount,
            @RequestParam(defaultValue = "CET-4") String category) {

        Map<String, Object> task = new HashMap<>();

        try {
            // 1. 获取需要复习的单词（下次复习时间已到）
            List<StudyRecord> reviewRecords = studyRecordRepository
                    .findByUserIdAndNextReviewTimeBefore(userId, LocalDateTime.now());

            // 提取需要复习的单词（去重）
            List<Word> reviewWords = reviewRecords.stream()
                    .map(StudyRecord::getWord)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // 2. 获取新单词（用户还没学过的）
            // 2.1 先获取该分类的所有单词
            List<Word> allCategoryWords = wordRepository.findByCategory(category);

            // 2.2 获取用户已学过的单词ID
            List<StudyRecord> userRecords = studyRecordRepository.findByUserId(userId);
            Set<Long> learnedWordIds = userRecords.stream()
                    .map(record -> record.getWord().getId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 2.3 筛选未学过的单词
            List<Word> newWords = allCategoryWords.stream()
                    .filter(word -> !learnedWordIds.contains(word.getId()))
                    .limit(newWordsCount)
                    .collect(Collectors.toList());

            // 3. 构建返回结果
            task.put("status", "success");
            task.put("reviewWords", reviewWords);
            task.put("reviewCount", reviewWords.size());
            task.put("newWords", newWords);
            task.put("newCount", newWords.size());
            task.put("totalToday", reviewWords.size() + newWords.size());
            task.put("date", LocalDateTime.now().toLocalDate().toString());

        } catch (Exception e) {
            task.put("status", "error");
            task.put("message", "获取今日任务失败: " + e.getMessage());
        }

        return task;
    }

    /**
     * 获取随机单词 - 新添加的方法
     */
    @GetMapping("/random")
    public List<Word> getRandomWords(@RequestParam(defaultValue = "10") int count) {
        List<Word> allWords = wordRepository.findAll();

        if (allWords.size() <= count) {
            return allWords;
        }

        // 简单的随机选择
        Collections.shuffle(allWords);
        return allWords.subList(0, Math.min(count, allWords.size()));
    }

    /**
     * 获取单词详情 - 新添加的方法
     */
    @GetMapping("/{wordId}")
    public Map<String, Object> getWordDetail(@PathVariable Long wordId) {
        Map<String, Object> response = new HashMap<>();

        Word word = wordRepository.findById(wordId).orElse(null);
        if (word == null) {
            response.put("status", "error");
            response.put("message", "单词不存在");
        } else {
            response.put("status", "success");
            response.put("word", word);
        }

        return response;
    }
}