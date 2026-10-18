package com.school.student_manage.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 前端单页应用（Vue3 构建产物位于 static/app）入口转发。
 *
 * <p>Vue Router 使用 hash 模式，因此把 {@code /app} 与 {@code /app/} 直接转发到
 * {@code /app/index.html} 即可，刷新任意子路由都不会 404。</p>
 *
 * <p>另外把根路径 {@code /} 与旧版页面地址（{@code /login.html}、{@code /admin.html}、
 * {@code /student.html}）重定向到新的 AI 应用 {@code /app/}，方便直接通过
 * {@code http://127.0.0.1:8080/} 进入，同时避免旧书签访问到 404。</p>
 *
 * <p>旧版页面文件已移除，备份见 {@code backup/legacy-static-pages/}。
 * 如需回退到旧界面，把备份文件复制回 {@code src/main/resources/static/}，
 * 并删除下面四个 {@code addRedirectViewController} 调用。</p>
 */
@Configuration
public class SpaForwardConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/app").setViewName("forward:/app/index.html");
        registry.addViewController("/app/").setViewName("forward:/app/index.html");
        // 入口统一跳转（可回退）：根路径与旧版页面地址 -> 新的 AI 应用
        registry.addRedirectViewController("/", "/app/");
        registry.addRedirectViewController("/login.html", "/app/");
        registry.addRedirectViewController("/admin.html", "/app/");
        registry.addRedirectViewController("/student.html", "/app/");
    }
}
