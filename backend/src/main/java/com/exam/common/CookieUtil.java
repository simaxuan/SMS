package com.exam.common;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Cookie 读取工具：供鉴权拦截器（AuthInterceptor）与认证控制器（AuthController）复用，
 * 消除此前两处各自实现的重复 readCookie 逻辑。
 */
public final class CookieUtil {

    private CookieUtil() {
    }

    /** 按名称读取请求中的 Cookie 值；不存在则返回 null。 */
    public static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cs = request.getCookies();
        if (cs == null) return null;
        for (Cookie c : cs) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
