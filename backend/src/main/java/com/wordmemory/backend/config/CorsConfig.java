package com.wordmemory.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 微信小程序开发环境配置
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")  // 使用allowedOriginPatterns而不是allowedOrigins
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With")
                .allowCredentials(false)  // 微信小程序通常不需要凭证
                .maxAge(3600);

        System.out.println("CORS配置已启用：允许跨域访问");
    }
}