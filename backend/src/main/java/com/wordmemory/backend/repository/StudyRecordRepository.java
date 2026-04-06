package com.wordmemory.backend.repository;

import com.wordmemory.backend.entity.StudyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;

@Repository
public interface StudyRecordRepository extends JpaRepository<StudyRecord, Long> {
    // 查找用户需要复习的单词
    List<StudyRecord> findByUserIdAndNextReviewTimeBefore(Long userId, LocalDateTime currentTime);

    // 查找用户的错词
    List<StudyRecord> findByUserIdAndIsCorrectFalse(Long userId);

    // 统计用户的学习记录数
    long countByUserId(Long userId);
    // 根据用户ID查找所有学习记录
    List<StudyRecord> findByUserId(Long userId);

    // 根据单词ID查找学习记录（删除单词时用）
    List<StudyRecord> findByWordId(Long wordId);

    // 获取用户-单词的最新一条学习记录（用于 SM-2 调度状态）
    StudyRecord findTopByUserIdAndWordIdOrderByStudyTimeDesc(Long userId, Long wordId);

    // BKT 在线推断：获取用户-单词的历史答题序列（按时间升序）
    @Query("SELECT r.isCorrect FROM StudyRecord r WHERE r.user.id = :userId AND r.word.id = :wordId ORDER BY r.studyTime ASC")
    List<Boolean> findIsCorrectByUserIdAndWordIdOrderByStudyTimeAsc(Long userId, Long wordId);
}