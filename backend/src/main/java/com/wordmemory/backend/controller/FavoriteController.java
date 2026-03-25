package com.wordmemory.backend.controller;

import com.wordmemory.backend.entity.Favorite;
import com.wordmemory.backend.entity.User;
import com.wordmemory.backend.entity.Word;
import com.wordmemory.backend.repository.FavoriteRepository;
import com.wordmemory.backend.repository.UserRepository;
import com.wordmemory.backend.repository.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    @Autowired private FavoriteRepository favoriteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WordRepository wordRepository;

    /** 获取用户收藏列表 */
    @GetMapping("/{userId}/list")
    public Map<String, Object> getFavorites(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Favorite> favorites = favoriteRepository.findByUserId(userId);
            List<Map<String, Object>> list = favorites.stream().map(f -> {
                Map<String, Object> item = new HashMap<>();
                Word w = f.getWord();
                item.put("favoriteId", f.getId());
                item.put("wordId", w.getId());
                item.put("word", w.getWord());
                item.put("meaning", w.getMeaning());
                item.put("pronunciation", w.getPronunciation());
                item.put("category", w.getCategory());
                item.put("createdAt", f.getCreatedAt());
                return item;
            }).collect(Collectors.toList());
            response.put("status", "success");
            response.put("favorites", list);
            response.put("total", list.size());
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 获取用户收藏的所有单词 ID 集合（用于前端批量判断是否已收藏） */
    @GetMapping("/{userId}/ids")
    public Map<String, Object> getFavoriteIds(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Set<Long> ids = favoriteRepository.findWordIdsByUserId(userId);
            response.put("status", "success");
            response.put("favoriteWordIds", ids);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 添加收藏 */
    @PostMapping("/add")
    public Map<String, Object> addFavorite(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long wordId = Long.valueOf(request.get("wordId").toString());
            if (favoriteRepository.existsByUserIdAndWordId(userId, wordId)) {
                response.put("status", "success");
                response.put("message", "already_favorited");
                response.put("favorited", true);
                return response;
            }
            User user = userRepository.findById(userId).orElseThrow();
            Word word = wordRepository.findById(wordId).orElseThrow();
            Favorite fav = new Favorite();
            fav.setUser(user);
            fav.setWord(word);
            favoriteRepository.save(fav);
            response.put("status", "success");
            response.put("message", "added");
            response.put("favorited", true);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 取消收藏 */
    @DeleteMapping("/remove")
    @Transactional
    public Map<String, Object> removeFavorite(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long wordId = Long.valueOf(request.get("wordId").toString());
            favoriteRepository.deleteByUserIdAndWordId(userId, wordId);
            response.put("status", "success");
            response.put("message", "removed");
            response.put("favorited", false);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 切换收藏状态（前端统一调用此接口） */
    @PostMapping("/toggle")
    @Transactional
    public Map<String, Object> toggleFavorite(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long wordId = Long.valueOf(request.get("wordId").toString());
            boolean exists = favoriteRepository.existsByUserIdAndWordId(userId, wordId);
            if (exists) {
                favoriteRepository.deleteByUserIdAndWordId(userId, wordId);
                response.put("favorited", false);
                response.put("message", "removed");
            } else {
                User user = userRepository.findById(userId).orElseThrow();
                Word word = wordRepository.findById(wordId).orElseThrow();
                Favorite fav = new Favorite();
                fav.setUser(user); fav.setWord(word);
                favoriteRepository.save(fav);
                response.put("favorited", true);
                response.put("message", "added");
            }
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /** 检查单个单词是否已收藏 */
    @GetMapping("/check")
    public Map<String, Object> checkFavorite(@RequestParam Long userId, @RequestParam Long wordId) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean favorited = favoriteRepository.existsByUserIdAndWordId(userId, wordId);
            response.put("status", "success");
            response.put("favorited", favorited);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }
}
