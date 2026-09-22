package com.exam.common;

import com.exam.entity.Account;

/**
 * 基于 ThreadLocal 的当前登录用户上下文（由 AuthInterceptor 填充）。
 */
public class LoginUserContext {

    private static final ThreadLocal<Account> HOLDER = new ThreadLocal<>();

    private LoginUserContext() {
    }

    public static void set(Account account) {
        HOLDER.set(account);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前登录用户，未登录返回 null */
    public static Account get() {
        return HOLDER.get();
    }

    /** 断言已登录，否则抛 401 */
    public static Account requireLogin() {
        Account a = HOLDER.get();
        if (a == null) {
            throw new BizException(401, "未登录或登录已失效");
        }
        return a;
    }

    /** 断言指定角色，否则抛 403 */
    public static Account requireRole(String role) {
        Account a = requireLogin();
        if (!role.equals(a.getRole())) {
            throw new BizException(403, "无权限执行该操作");
        }
        return a;
    }
}
