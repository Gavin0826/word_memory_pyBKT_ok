package com.wordmemory.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * word_mastery 表：存储每个用户-单词的最新 pyBKT 掌握概率。
 * 唯一约束 (user_id, word_id)：每对用户-单词只保留最新一条。
 */
@Data
@Entity
@Table(name = "word_mastery",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_word", columnNames = {"user_id", "word_id"}
        ))
public class WordMastery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "word_id", nullable = false)
    private Long wordId;

    /** pyBKT 预测的掌握概率，范围 [0.0, 1.0] */
    @Column(name = "mastery_probability", nullable = false)
    private Double masteryProbability;

    /** 模型版本号，便于追溯（如 "v1.0"）*/
    @Column(name = "model_version", length = 32)
    private String modelVersion = "v1.0";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpsert() {
        updatedAt = LocalDateTime.now();
    }
}
