package com.wordmemory.backend.repository;

import com.wordmemory.backend.entity.WordMastery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface WordMasteryRepository extends JpaRepository<WordMastery, Long> {

    /** 查询某用户-单词的最新掌握概率记录 */
    Optional<WordMastery> findByUserIdAndWordId(Long userId, Long wordId);

    /**
     * 插入或更新（upsert）：若已存在则更新概率与时间，否则插入新行。
     * 依赖 word_mastery 表上的唯一约束 uk_user_word。
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO word_mastery (user_id, word_id, mastery_probability, model_version, updated_at)
            VALUES (:userId, :wordId, :prob, :version, NOW())
            ON DUPLICATE KEY UPDATE
                mastery_probability = VALUES(mastery_probability),
                model_version       = VALUES(model_version),
                updated_at          = NOW()
            """, nativeQuery = true)
    void upsertMastery(@Param("userId") Long userId,
                       @Param("wordId") Long wordId,
                       @Param("prob") Double prob,
                       @Param("version") String version);
}
