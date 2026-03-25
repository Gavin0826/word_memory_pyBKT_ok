package com.wordmemory.backend.repository;

import com.wordmemory.backend.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    // 根据会话令牌查找
    Optional<UserSession> findBySessionToken(String sessionToken);

    // 根据用户ID查找所有会话
    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId")
    java.util.List<UserSession> findByUserId(@Param("userId") Long userId);

    // 删除过期的会话
    @Modifying
    @Transactional
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    void deleteExpiredSessions(@Param("now") LocalDateTime now);

    // 根据用户ID删除所有会话
    @Modifying
    @Transactional
    void deleteByUserId(Long userId);

    // 检查会话是否存在
    boolean existsBySessionToken(String sessionToken);
}