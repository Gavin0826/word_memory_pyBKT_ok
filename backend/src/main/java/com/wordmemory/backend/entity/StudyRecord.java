package com.wordmemory.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;


@Data
@Entity
@Table(name = "study_record")

public class StudyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;                // 关联用户

    @ManyToOne
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;                // 关联单词

    @Column(nullable = false)
    private Boolean isCorrect = false; // 是否答对

    @Column(nullable = false)
    private LocalDateTime studyTime;  // 学习时间

    @Column(nullable = false)
    private String studyType = "new"; // 学习类型

    private Integer reviewStage = 0;  // 复习阶段（0=新学）

    private Integer consecutiveCorrect = 0; // 连续答对次数 - 新增字段

    // ==================== SM-2 调度状态 ====================
    // 说明：由于当前实现“每次答题都创建新记录”，所以最新一条记录里保存的是该单词的最新调度状态。
    // 0) repetition：成功复习次数（SM-2 terminology）
    // 1) easeFactor：易记性因子 EF，范围 >= 1.3
    // 2) intervalDays：下一次间隔（天）
    private Double easeFactor = 2.5;

    private Integer repetition = 0;

    private Integer intervalDays = 0;

    private LocalDateTime nextReviewTime; // 下次复习时间

    @PrePersist
    protected void onCreate() {
        // studyTime is set explicitly in the controller
    }
}
