package com.labelhub.modules.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.labelhub.common.constant.RedisKeyConstant;
import com.labelhub.common.constant.RoleConstant;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.exception.ErrorCode;
import com.labelhub.common.util.RedisUtil;
import com.labelhub.modules.user.convert.UserConvert;
import com.labelhub.modules.user.dto.RegisterDTO;
import com.labelhub.modules.user.dto.UserDTO;
import com.labelhub.modules.user.dto.UserRoleQueryDTO;
import com.labelhub.modules.user.entity.Role;
import com.labelhub.modules.user.entity.User;
import com.labelhub.modules.user.entity.UserRole;
import com.labelhub.modules.user.mapper.RoleMapper;
import com.labelhub.modules.user.mapper.UserMapper;
import com.labelhub.modules.user.mapper.UserRoleMapper;
import com.labelhub.modules.user.service.UserService;
import com.labelhub.modules.user.vo.UserRoleVO;
import com.labelhub.modules.user.vo.UserRoleUserVO;
import com.labelhub.modules.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final long ROLE_CACHE_TIMEOUT = 30L;

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;
    private final UserConvert userConvert;

    @Override
    @Transactional
    public void register(RegisterDTO dto) {
        log.info("用户注册开始：{}", dto.getUsername());
        try {
            User existing = findByUsername(dto.getUsername());
            if (existing != null) {
                log.warn("用户注册失败，用户名已存在：{}", dto.getUsername());
                throw new BusinessException(ErrorCode.USERNAME_EXISTS);
            }

            User user = userConvert.toEntity(dto);
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
            user.setStatus(1);
            save(user);

            Role labelerRole = roleMapper.selectOne(
                    new LambdaQueryWrapper<Role>().eq(Role::getRoleKey, RoleConstant.LABELER));
            if (labelerRole != null) {
                UserRole userRole = new UserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(labelerRole.getId());
                userRoleMapper.insert(userRole);
                cacheUserRoles(user.getId(), List.of(RoleConstant.LABELER));
            }
            log.info("用户注册成功：{}", dto.getUsername());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("用户注册异常：{}", e.getMessage(), e);
            throw new BusinessException("用户注册失败：" + e.getMessage());
        }
    }

    @Override
    public User findByUsername(String username) {
        log.info("根据用户名查询用户开始：{}", username);
        try {
            User user = getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            log.info("根据用户名查询用户成功：{}，exists={}", username, user != null);
            return user;
        } catch (Exception e) {
            log.error("根据用户名查询用户失败：{}", e.getMessage(), e);
            throw new BusinessException("查询用户失败：" + e.getMessage());
        }
    }

    @Override
    public UserVO getCurrentUserInfo() {
        log.info("获取当前用户信息开始");
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = getById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            UserVO vo = userConvert.toVO(user);
            log.info("获取当前用户信息成功：userId={}", userId);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取当前用户信息异常：{}", e.getMessage(), e);
            throw new BusinessException("获取当前用户信息失败：" + e.getMessage());
        }
    }

    @Override
    public void updateProfile(UserDTO dto) {
        log.info("修改个人资料开始");
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userConvert.toEntity(dto);
            user.setId(userId);
            updateById(user);
            log.info("修改个人资料成功，用户ID：{}", userId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("修改个人资料异常：{}", e.getMessage(), e);
            throw new BusinessException("修改个人资料失败：" + e.getMessage());
        }
    }

    @Override
    public void updatePassword(String oldPassword, String newPassword) {
        log.info("修改密码开始");
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = getById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }

            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                log.warn("修改密码失败，原密码不匹配，用户ID：{}", userId);
                throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR);
            }

            User update = new User();
            update.setId(userId);
            update.setPassword(passwordEncoder.encode(newPassword));
            updateById(update);
            log.info("修改密码成功，用户ID：{}", userId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("修改密码异常：{}", e.getMessage(), e);
            throw new BusinessException("修改密码失败：" + e.getMessage());
        }
    }

    @Override
    public UserRoleVO getCurrentUserRoles() {
        log.info("获取当前用户角色身份开始");
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            String cacheKey = buildRoleCacheKey(userId);
            List<String> cachedRoles = redisUtil.getList(cacheKey);
            UserRoleVO vo = new UserRoleVO();
            vo.setUserId(userId);
            if (cachedRoles != null) {
                vo.setRoles(cachedRoles);
                log.info("获取当前用户角色身份成功：userId={}，命中缓存", userId);
                return vo;
            }

            List<String> roles = userRoleMapper.selectRoleKeysByUserId(userId);
            cacheUserRoles(userId, roles);
            vo.setRoles(roles);
            log.info("获取当前用户角色身份成功：userId={}，访问数据库", userId);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取当前用户角色身份异常：{}", e.getMessage(), e);
            throw new BusinessException("获取当前用户角色身份失败：" + e.getMessage());
        }
    }

    @Override
    public List<String> getUserRoles(Long userId) {
        log.info("获取用户角色列表开始：userId={}", userId);
        try {
            String cacheKey = buildRoleCacheKey(userId);
            List<String> cachedRoles = redisUtil.getList(cacheKey);
            if (cachedRoles != null) {
                log.info("获取用户角色列表成功：userId={}，命中缓存", userId);
                return cachedRoles;
            }

            List<String> roles = userRoleMapper.selectRoleKeysByUserId(userId);
            cacheUserRoles(userId, roles);
            log.info("获取用户角色列表成功：userId={}，访问数据库", userId);
            return roles;
        } catch (Exception e) {
            log.error("获取用户角色列表失败：{}", e.getMessage(), e);
            throw new BusinessException("获取用户角色列表失败：" + e.getMessage());
        }
    }

    @Override
    public Page<UserRoleUserVO> listUsersByRole(String roleKey, UserRoleQueryDTO dto) {
        log.info("按角色查询用户列表开始：roleKey={}，nickname={}，status={}",
                roleKey, dto.getNickname(), dto.getStatus());
        try {
            Page<UserRoleUserVO> page = new Page<>(dto.getPageNum(), dto.getPageSize());
            Page<UserRoleUserVO> result = baseMapper.selectUsersByRole(
                    page, roleKey, dto.getNickname(), dto.getStatus());
            log.info("按角色查询用户列表成功：roleKey={}，total={}", roleKey, result.getTotal());
            return result;
        } catch (Exception e) {
            log.error("按角色查询用户列表失败：{}", e.getMessage(), e);
            throw new BusinessException("按角色查询用户列表失败：" + e.getMessage());
        }
    }

    private void cacheUserRoles(Long userId, List<String> roles) {
        redisUtil.setList(buildRoleCacheKey(userId), roles, ROLE_CACHE_TIMEOUT, TimeUnit.MINUTES);
    }

    private String buildRoleCacheKey(Long userId) {
        return RedisKeyConstant.SATOKEN_ROLES + userId;
    }
}
