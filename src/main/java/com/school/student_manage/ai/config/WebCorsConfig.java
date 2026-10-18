package com.school.student_manage.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 前后端分离开发时的跨域配置。
 *
 * <p>Vue3 前端在开发态由 Vite 代理（同源），但为方便评委直接访问、以及前后端分别部署的场景，
 * 这里放开跨域并允许携带 Session Cookie（前端接口依赖登录态）。</p>
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
