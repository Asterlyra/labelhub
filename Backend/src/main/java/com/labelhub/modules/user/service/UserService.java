package com.labelhub.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.modules.user.entity.User;
import com.labelhub.modules.user.dto.RegisterDTO;
import com.labelhub.modules.user.dto.UserDTO;
import com.labelhub.modules.user.dto.UserRoleQueryDTO;
import com.labelhub.modules.user.vo.UserRoleVO;
import com.labelhub.modules.user.vo.UserRoleUserVO;
import com.labelhub.modules.user.vo.UserVO;

import java.util.List;

public interface UserService extends IService<User> {

    /**
     * 用户注册
     */
    void register(RegisterDTO dto);

    /**
     * 根据用户名查找用户
     */
    User findByUsername(String username);

    /**
     * 获取当前登录用户信息
     */
    UserVO getCurrentUserInfo();

    /**
     * 修改个人信息
     */
    void updateProfile(UserDTO dto);

    /**
     * 修改密码
     */
    void updatePassword(String oldPassword, String newPassword);

    /**
     * 获取当前用户角色身份
     */
    UserRoleVO getCurrentUserRoles();

    /**
     * 获取指定用户角色身份，优先读取 Redis 缓存
     */
    List<String> getUserRoles(Long userId);

    /**
     * 按系统角色查询用户列表
     */
    Page<UserRoleUserVO> listUsersByRole(String roleKey, UserRoleQueryDTO dto);
}
