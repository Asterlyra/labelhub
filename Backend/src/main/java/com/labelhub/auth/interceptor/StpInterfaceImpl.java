package com.labelhub.auth.interceptor;

import cn.dev33.satoken.stp.StpInterface;
import com.labelhub.common.constant.RedisKeyConstant;
import com.labelhub.common.util.RedisUtil;
import com.labelhub.modules.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.labelhub.common.constant.RedisKeyConstant.SATOKEN_TIMEOUT;

@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserRoleMapper userRoleMapper;
    private final RedisUtil redisUtil;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = Long.parseLong(loginId.toString());
        String key = RedisKeyConstant.SATOKEN_PERMS + userId;

        List<String> cached = redisUtil.getList(key);
        if (cached != null) {
            return cached;
        }

        List<String> permissions = userRoleMapper.selectPermissionKeysByUserId(userId);
        redisUtil.setList(key, permissions, SATOKEN_TIMEOUT, TimeUnit.MINUTES);
        return permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.parseLong(loginId.toString());
        String key = RedisKeyConstant.SATOKEN_ROLES + userId;

        List<String> cached = redisUtil.getList(key);
        if (cached != null) {
            return cached;
        }

        List<String> roles = userRoleMapper.selectRoleKeysByUserId(userId);
        redisUtil.setList(key, roles, SATOKEN_TIMEOUT, TimeUnit.MINUTES);
        return roles;
    }

    /**
     * 清除指定用户的鉴权缓存（角色/权限变更时调用）
     */
    public void clearAuthCache(Long userId) {
        redisUtil.delete(RedisKeyConstant.SATOKEN_ROLES + userId);
        redisUtil.delete(RedisKeyConstant.SATOKEN_PERMS + userId);
    }
}
