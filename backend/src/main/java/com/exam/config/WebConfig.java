package com.exam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 只对业务 API 启用登录拦截；/api/auth/**（注册/登录）与静态资源(assets/* )放行，
 * 避免影响 SPA 与 MIME。SpaForwardController 的 @Controller 映射不受影响。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 仅放行无需登录的登录/注册/登出三接口；/api/auth/me 仍需有效 token 通过拦截器，
        // 否则 LoginUserContext 不会填充，requireLogin() 必然 401。
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register", "/api/auth/logout");
    }
}
