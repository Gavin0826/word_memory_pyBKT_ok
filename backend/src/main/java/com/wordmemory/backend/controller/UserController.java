package com.wordmemory.backend.controller;

import com.wordmemory.backend.entity.StudyRecord;
import com.wordmemory.backend.entity.User;
import com.wordmemory.backend.entity.UserSession;
import com.wordmemory.backend.repository.StudyRecordRepository;
import com.wordmemory.backend.repository.UserRepository;
import com.wordmemory.backend.repository.UserSessionRepository;
import com.wordmemory.backend.util.PasswordUtil;
import com.wordmemory.backend.util.StudyStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private StudyRecordRepository studyRecordRepository;

    @Autowired
    private StudyStatsService studyStatsService;

    // 会话过期时间（30天）
    private static final int SESSION_EXPIRE_DAYS = 30;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String username = request.get("username");
        String email = request.get("email");
        String password = request.get("password");
        String confirmPassword = request.get("confirmPassword");

        // 1. 验证必填字段
        if (username == null || username.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "用户名不能为空");
            return response;
        }

        if (password == null || password.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "密码不能为空");
            return response;
        }

        // 2. 验证密码确认
        if (!password.equals(confirmPassword)) {
            response.put("status", "error");
            response.put("message", "两次输入的密码不一致");
            return response;
        }

        // 3. 验证密码强度（至少6位）
        if (password.length() < 6) {
            response.put("status", "error");
            response.put("message", "密码长度至少6位");
            return response;
        }

        // 4. 检查用户名是否已存在
        if (userRepository.existsByUsername(username)) {
            response.put("status", "error");
            response.put("message", "用户名已存在");
            return response;
        }

        // 5. 检查邮箱是否已存在（如果有邮箱）
        if (email != null && !email.trim().isEmpty()) {
            if (userRepository.existsByEmail(email)) {
                response.put("status", "error");
                response.put("message", "邮箱已被注册");
                return response;
            }
        }

        try {
            // 6. 创建新用户
            User user = new User();
            user.setUsername(username.trim());

            if (email != null && !email.trim().isEmpty()) {
                user.setEmail(email.trim());
            }

            // 使用 PasswordUtil 加密密码
            user.setPasswordHash(PasswordUtil.encryptPassword(password));
            user.setTotalWords(0);
            user.setStudiedDays(0);
            user.setLastLogin(LocalDateTime.now());

            userRepository.save(user);

            response.put("status", "success");
            response.put("message", "注册成功");
            response.put("userId", user.getId());

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "注册失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String identifier = request.get("identifier"); // 用户名或邮箱
        String password = request.get("password");
        boolean rememberMe = Boolean.parseBoolean(request.getOrDefault("rememberMe", "false"));

        // 1. 验证参数
        if (identifier == null || identifier.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "请输入用户名或邮箱");
            return response;
        }

        if (password == null || password.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "请输入密码");
            return response;
        }

        try {
            // 2. 查找用户（支持用户名或邮箱登录）
            Optional<User> userOptional;
            if (identifier.contains("@")) {
                // 按邮箱查找
                userOptional = userRepository.findByEmail(identifier.trim());
            } else {
                // 按用户名查找
                User user = userRepository.findByUsername(identifier.trim());
                userOptional = Optional.ofNullable(user);
            }

            if (!userOptional.isPresent()) {
                response.put("status", "error");
                response.put("message", "用户名或密码错误");
                return response;
            }

            User user = userOptional.get();

            // 3. 使用 PasswordUtil 验证密码
            if (!PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
                response.put("status", "error");
                response.put("message", "用户名或密码错误");
                return response;
            }

            // 4. 更新最后登录时间
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            // 5. 创建会话
            String sessionToken = PasswordUtil.generateToken();
            UserSession session = new UserSession();
            session.setUser(user);
            session.setSessionToken(sessionToken);

            if (rememberMe) {
                // 记住我：30天有效期
                session.setExpiresAt(LocalDateTime.now().plusDays(SESSION_EXPIRE_DAYS));
            } else {
                // 不记住：7天有效期
                session.setExpiresAt(LocalDateTime.now().plusDays(7));
            }

            sessionRepository.save(session);

            // 6. 清理过期的会话
            sessionRepository.deleteExpiredSessions(LocalDateTime.now());

            // 7. 构建响应
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("email", user.getEmail());
            userInfo.put("totalWords", user.getTotalWords());
            userInfo.put("studiedDays", user.getStudiedDays());
            userInfo.put("lastLogin", user.getLastLogin());

            response.put("status", "success");
            response.put("message", "登录成功");
            response.put("user", userInfo);
            response.put("sessionToken", sessionToken);
            response.put("expiresAt", session.getExpiresAt());

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "登录失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 验证会话
     */
    @PostMapping("/validate-session")
    public Map<String, Object> validateSession(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> response = new HashMap<>();

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("status", "error");
            response.put("message", "未提供有效的会话令牌");
            return response;
        }

        String sessionToken = authHeader.substring(7);

        try {
            Optional<UserSession> sessionOptional = sessionRepository.findBySessionToken(sessionToken);

            if (!sessionOptional.isPresent()) {
                response.put("status", "error");
                response.put("message", "会话已过期或无效");
                return response;
            }

            UserSession session = sessionOptional.get();

            // 检查是否过期
            if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
                sessionRepository.delete(session);
                response.put("status", "error");
                response.put("message", "会话已过期");
                return response;
            }

            // 延长会话有效期（可选）
            if (session.getExpiresAt().isBefore(LocalDateTime.now().plusDays(1))) {
                session.setExpiresAt(LocalDateTime.now().plusDays(SESSION_EXPIRE_DAYS));
                sessionRepository.save(session);
            }

            User user = session.getUser();

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("email", user.getEmail());
            userInfo.put("totalWords", user.getTotalWords());
            userInfo.put("studiedDays", user.getStudiedDays());
            userInfo.put("lastLogin", user.getLastLogin());

            response.put("status", "success");
            response.put("message", "会话有效");
            response.put("user", userInfo);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "验证会话失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> response = new HashMap<>();

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("status", "error");
            response.put("message", "未提供有效的会话令牌");
            return response;
        }

        String sessionToken = authHeader.substring(7);

        try {
            Optional<UserSession> sessionOptional = sessionRepository.findBySessionToken(sessionToken);

            if (sessionOptional.isPresent()) {
                sessionRepository.delete(sessionOptional.get());
            }

            response.put("status", "success");
            response.put("message", "退出登录成功");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "退出登录失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/info/{userId}")
    public Map<String, Object> getUserInfo(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> userOptional = userRepository.findById(userId);

            if (!userOptional.isPresent()) {
                response.put("status", "error");
                response.put("message", "用户不存在");
                return response;
            }

            User user = userOptional.get();

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("email", user.getEmail());
            userInfo.put("totalWords", user.getTotalWords());
            userInfo.put("studiedDays", user.getStudiedDays());
            userInfo.put("lastLogin", user.getLastLogin());
            userInfo.put("createdAt", user.getCreatedAt());

            response.put("status", "success");
            response.put("user", userInfo);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "获取用户信息失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public Map<String, Object> changePassword(
            @RequestHeader(value = "Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();

        // 1. 提取会话令牌
        String sessionToken;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            sessionToken = authHeader.substring(7);
        } else {
            response.put("status", "error");
            response.put("message", "未提供有效的会话令牌");
            return response;
        }

        // 2. 获取请求参数
        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");
        String confirmPassword = request.get("confirmPassword");

        // 3. 验证参数
        if (oldPassword == null || oldPassword.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "请输入原密码");
            return response;
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "请输入新密码");
            return response;
        }

        if (!newPassword.equals(confirmPassword)) {
            response.put("status", "error");
            response.put("message", "两次输入的新密码不一致");
            return response;
        }

        if (newPassword.length() < 6) {
            response.put("status", "error");
            response.put("message", "新密码长度至少6位");
            return response;
        }

        try {
            // 4. 验证会话
            Optional<UserSession> sessionOptional = sessionRepository.findBySessionToken(sessionToken);
            if (!sessionOptional.isPresent()) {
                response.put("status", "error");
                response.put("message", "会话无效");
                return response;
            }

            User user = sessionOptional.get().getUser();

            // 5. 验证旧密码
            if (!PasswordUtil.verifyPassword(oldPassword, user.getPasswordHash())) {
                response.put("status", "error");
                response.put("message", "原密码错误");
                return response;
            }

            // 6. 更新密码
            user.setPasswordHash(PasswordUtil.encryptPassword(newPassword));
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            response.put("status", "success");
            response.put("message", "密码修改成功");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "修改密码失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/update")
    public Map<String, Object> updateUser(
            @RequestHeader(value = "Authorization") String authHeader,
            @RequestBody Map<String, Object> updateData) {

        Map<String, Object> response = new HashMap<>();

        // 1. 提取会话令牌
        String sessionToken;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            sessionToken = authHeader.substring(7);
        } else {
            response.put("status", "error");
            response.put("message", "未提供有效的会话令牌");
            return response;
        }

        try {
            // 2. 验证会话
            Optional<UserSession> sessionOptional = sessionRepository.findBySessionToken(sessionToken);
            if (!sessionOptional.isPresent()) {
                response.put("status", "error");
                response.put("message", "会话无效");
                return response;
            }

            User user = sessionOptional.get().getUser();
            boolean hasUpdate = false;

            // 3. 更新用户名（如果提供了且与当前不同）
            if (updateData.containsKey("username")) {
                String newUsername = (String) updateData.get("username");
                if (newUsername != null && !newUsername.trim().isEmpty() && !newUsername.trim().equals(user.getUsername())) {
                    // 检查新用户名是否已存在
                    if (userRepository.existsByUsername(newUsername.trim())) {
                        response.put("status", "error");
                        response.put("message", "用户名已存在");
                        return response;
                    }
                    user.setUsername(newUsername.trim());
                    hasUpdate = true;
                }
            }

            // 4. 更新邮箱（如果提供了且与当前不同）
            if (updateData.containsKey("email")) {
                String newEmail = (String) updateData.get("email");
                if (newEmail != null && !newEmail.trim().isEmpty() &&
                        (user.getEmail() == null || !newEmail.trim().equals(user.getEmail()))) {
                    // 检查新邮箱是否已存在
                    if (userRepository.existsByEmail(newEmail.trim())) {
                        response.put("status", "error");
                        response.put("message", "邮箱已被使用");
                        return response;
                    }
                    user.setEmail(newEmail.trim());
                    hasUpdate = true;
                }
            }

            // 5. 保存更新
            if (hasUpdate) {
                user.setUpdatedAt(LocalDateTime.now());
                userRepository.save(user);

                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("username", user.getUsername());
                userInfo.put("email", user.getEmail());
                userInfo.put("totalWords", user.getTotalWords());
                userInfo.put("studiedDays", user.getStudiedDays());
                userInfo.put("lastLogin", user.getLastLogin());
                userInfo.put("updatedAt", user.getUpdatedAt());

                response.put("status", "success");
                response.put("message", "用户信息更新成功");
                response.put("user", userInfo);
            } else {
                response.put("status", "success");
                response.put("message", "没有需要更新的信息");
            }

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "更新用户信息失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 获取所有用户（管理员用）
     */
    @GetMapping("/all")
    public Map<String, Object> getAllUsers() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<User> users = userRepository.findAll();
            List<Map<String, Object>> userList = new ArrayList<>();
            for (User user : users) {
                Map<String, Object> stats = studyStatsService.buildUserStats(user.getId());
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("username", user.getUsername());
                userInfo.put("email", user.getEmail());
                userInfo.put("totalWords", stats.get("masteredWords"));
                userInfo.put("studiedDays", stats.get("studiedDays"));
                userInfo.put("lastLogin", user.getLastLogin());
                userInfo.put("createdAt", user.getCreatedAt());
                userList.add(userInfo);
            }
            response.put("status", "success");
            response.put("count", userList.size());
            response.put("users", userList);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "获取用户列表失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 获取指定用户学习进度详情（管理员用）
     */
    @GetMapping("/admin/study-progress/{userId}")
    public Map<String, Object> getUserStudyProgress(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (!userOpt.isPresent()) {
                response.put("status", "error");
                response.put("message", "用户不存在");
                return response;
            }
            User user = userOpt.get();
            Map<String, Object> stats = studyStatsService.buildUserStats(userId);

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("email", user.getEmail());
            userInfo.put("totalWords", stats.get("masteredWords"));
            userInfo.put("studiedDays", stats.get("studiedDays"));
            userInfo.put("lastLogin", user.getLastLogin());
            userInfo.put("createdAt", user.getCreatedAt());
            response.put("status", "success");
            response.put("user", userInfo);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "获取学习进度失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 获取用户学习统计（原有的接口，保持兼容）
     */
    @GetMapping("/{userId}/stats")
    public Map<String, Object> getUserStats(@PathVariable Long userId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            Optional<User> userOptional = userRepository.findById(userId);

            if (!userOptional.isPresent()) {
                stats.put("status", "error");
                stats.put("message", "用户不存在");
                return stats;
            }

            User user = userOptional.get();
            stats.put("userId", user.getId());
            stats.put("username", user.getUsername());
            stats.put("totalWords", user.getTotalWords());
            stats.put("studiedDays", user.getStudiedDays());
            stats.put("lastLogin", user.getLastLogin());
            stats.put("status", "success");

        } catch (Exception e) {
            stats.put("status", "error");
            stats.put("message", "获取用户统计失败: " + e.getMessage());
        }

        return stats;
    }

    /**
     * 重置密码（管理员功能）
     */
    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String identifier = request.get("identifier"); // 用户名或邮箱
        String newPassword = request.get("newPassword");

        // 验证参数
        if (identifier == null || identifier.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "请输入用户名或邮箱");
            return response;
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "请输入新密码");
            return response;
        }

        if (newPassword.length() < 6) {
            response.put("status", "error");
            response.put("message", "新密码长度至少6位");
            return response;
        }

        try {
            // 查找用户
            Optional<User> userOptional;
            if (identifier.contains("@")) {
                userOptional = userRepository.findByEmail(identifier.trim());
            } else {
                User user = userRepository.findByUsername(identifier.trim());
                userOptional = Optional.ofNullable(user);
            }

            if (!userOptional.isPresent()) {
                response.put("status", "error");
                response.put("message", "用户不存在");
                return response;
            }

            User user = userOptional.get();

            // 重置密码
            user.setPasswordHash(PasswordUtil.encryptPassword(newPassword));
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            response.put("status", "success");
            response.put("message", "密码重置成功");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "重置密码失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 检查用户名是否可用
     */
    @GetMapping("/check-username")
    public Map<String, Object> checkUsername(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean exists = userRepository.existsByUsername(username);
            response.put("status", "success");
            response.put("username", username);
            response.put("available", !exists);
            response.put("message", exists ? "用户名已存在" : "用户名可用");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "检查用户名失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 检查邮箱是否可用
     */
    @GetMapping("/check-email")
    public Map<String, Object> checkEmail(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean exists = userRepository.existsByEmail(email);
            response.put("status", "success");
            response.put("email", email);
            response.put("available", !exists);
            response.put("message", exists ? "邮箱已被注册" : "邮箱可用");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "检查邮箱失败: " + e.getMessage());
        }

        return response;
    }
}