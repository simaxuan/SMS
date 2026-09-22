package com.exam.config;

import com.exam.common.CookieUtil;
import com.exam.common.LoginUserContext;
import com.exam.controller.AuthController;
import com.exam.entity.Account;
import com.exam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    /**
     * 允许的跨源白名单（CSRF Origin 校验）。来自配置 app.security.allowed-origins（逗号分隔）；
     * 生产同源请求 Origin=自身 host 亦放行（见 isAllowedOrigin）。
     */
    private final Set<String> allowedOrigins;

    public AuthInterceptor(AuthService authService,
                           @Value("${app.security.allowed-origins:}") String allowedOrigins) {
        this.authService = authService;
        this.allowedOrigins = allowedOrigins == null || allowedOrigins.isBlank()
                ? Collections.emptySet()
                : Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 注：安全响应头由全局 SecurityHeadersFilter 统一附加（覆盖登录等放行路径），此处不再重复设置。

        // P2-2 可观测性：每请求生成 traceId 写入 MDC，便于日志串联排障（afterCompletion 清除）
        MDC.put("traceId", UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        MDC.put("uri", request.getMethod() + " " + request.getRequestURI());

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // CSRF 纵深防御：跨源写操作拒绝（SameSite=Lax 已挡主流浏览器；此处兜底 API/脚本）
        String method = request.getMethod();
        if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)
                && !HttpMethod.OPTIONS.matches(method)) {
            String origin = request.getHeader("Origin");
            // #10 说明：此处仅当 Origin 非空才校验。Origin:null 的跨站写请求交由 SameSite=Lax Cookie
            // 兜底拒绝（主流浏览器均遵守）；彻底拒绝 Origin:null 会误伤部分合法同源/隐私上下文请求，
            // 故保持现状作为纵深（真实隔离仍由 Cookie SameSite + 服务端鉴权保证）。
            if (origin != null && !isAllowedOrigin(request, origin)) {
                reject(response, 403, "跨站请求被拒绝");
                return false;
            }
        }

        String token = request.getHeader("X-Token");
        if (token == null || token.isBlank()) {
            token = CookieUtil.readCookie(request, AuthController.TOKEN_COOKIE);
        }
        Account account = authService.findByToken(token);
        if (account == null) {
            reject(response, 401, "未登录或登录已失效");
            return false;
        }
        LoginUserContext.set(account);
        MDC.put("userId", String.valueOf(account.getId()));
        return true;
    }

    private boolean isAllowedOrigin(HttpServletRequest request, String origin) {
        if (allowedOrigins.contains(origin)) return true;
        // 同源：Origin 的 scheme+host(含端口) 与当前请求一致
        String self = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443
                   ? "" : ":" + request.getServerPort());
        return self.equalsIgnoreCase(origin);
    }

    private void reject(HttpServletResponse response, int status, String msg) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + msg + "\"}");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        LoginUserContext.clear();
        MDC.clear();
    }
}
