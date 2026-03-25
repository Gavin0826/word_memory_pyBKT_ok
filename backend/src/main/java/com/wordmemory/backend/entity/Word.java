package com.wordmemory.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "word")

public class Word {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String word;              // 英文单词

    private String pronunciation;     // 音标

    @Column(length = 500)
    private String meaning;           // 中文释义（简化长度）

    private String difficulty = "medium";  // 难度

    private String category = "CET-4";     // 分类
}
