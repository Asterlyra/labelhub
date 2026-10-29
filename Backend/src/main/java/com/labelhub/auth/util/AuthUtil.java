package com.labelhub.auth.util;

import cn.dev33.satoken.stp.StpUtil;

public class AuthUtil {

    /**
     * 获取当前登录用户ID
     */
    public static Long getCurrentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 判断是否已登录
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    /**
     * 判断当前用户是否拥有指定角色
     */
    public static boolean hasRole(String role) {
        return StpUtil.hasRole(role);
    }

    /**
     * 判断当前用户是否拥有指定权限
     */
    public static boolean hasPermission(String permission) {
        return StpUtil.hasPermission(permission);
    }
}
