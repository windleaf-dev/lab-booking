package com.lab.common;

/**
 * 当前请求的登录用户上下文，由 AuthInterceptor 填充。
 */
public final class AuthContext {

    private static final ThreadLocal<JwtUtil.Claims> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(JwtUtil.Claims claims) {
        HOLDER.set(claims);
    }

    public static JwtUtil.Claims get() {
        return HOLDER.get();
    }

    public static JwtUtil.Claims require() {
        JwtUtil.Claims claims = HOLDER.get();
        if (claims == null) {
            throw BizException.unauthorized("未登录");
        }
        return claims;
    }

    /** 管理员或教师 */
    public static void requireStaff() {
        String role = require().role();
        if (!"ADMIN".equals(role) && !"TEACHER".equals(role)) {
            throw BizException.forbidden("仅管理员或教师可执行该操作");
        }
    }

    public static void requireAdmin() {
        if (!"ADMIN".equals(require().role())) {
            throw BizException.forbidden("仅管理员可执行该操作");
        }
    }

    public static void clear() {
        HOLDER.remove();
    }
}
