package com.wordmemory.backend.repository;

import com.wordmemory.backend.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WordRepository extends JpaRepository<Word, Long> {
    // 按分类查找单词
    List<Word> findByCategory(String category);

    // 统计分类单词数
    long countByCategory(String category);
}