package com.exam.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全局安全响应头过滤器。
 *
 * <p>对<strong>所有</strong>经由本应用的响应（含登录/注册等被 AuthInterceptor 放行的公开接口）
 * 统一附加安全响应头，避免仅依赖拦截器导致公开路径漏加。
 * CSP 采用同源收紧策略：脚本/样式同源，内联样式(ElementPlus/行内)放行，
 * 图片与字体允许 data:(ECharts/图标)，禁止 object 与跨源 connect。
 *
 * <p>注意：登出路径亦走本过滤器，保证安全头纵向一致。
 */
@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; " +
            "object-src 'none'; base-uri 'self'; frame-ancestors 'self'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        chain.doFilter(request, response);
    }
}
