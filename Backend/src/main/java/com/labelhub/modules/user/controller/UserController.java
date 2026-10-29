package com.labelhub.modules.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.common.annotation.RateLimit;
import com.labelhub.common.constant.RoleConstant;
import com.labelhub.common.result.R;
import com.labelhub.modules.user.dto.UserDTO;
import com.labelhub.modules.user.dto.UserRoleQueryDTO;
import com.labelhub.modules.user.service.UserService;
import com.labelhub.modules.user.vo.UserRoleVO;
import com.labelhub.modules.user.vo.UserRoleUserVO;
import com.labelhub.modules.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息。
     */
    @GetMapping("/info")
    public R<UserVO> getUserInfo() {
        return R.ok(userService.getCurrentUserInfo());
    }

    /**
     * 获取当前登录用户角色身份。
     */
    @GetMapping("/roles")
    public R<UserRoleVO> getUserRoles() {
        return R.ok(userService.getCurrentUserRoles());
    }

    /**
     * 查询所有启用标注员，可按昵称模糊匹配。
     */
    @GetMapping("/labelers")
    public R<Page<UserRoleUserVO>> listLabelers(UserRoleQueryDTO dto) {
        return R.ok(userService.listUsersByRole(RoleConstant.LABELER, dto));
    }

    /**
     * 查询所有启用审核员，可按昵称模糊匹配。
     */
    @GetMapping("/reviewers")
    public R<Page<UserRoleUserVO>> listReviewers(UserRoleQueryDTO dto) {
        return R.ok(userService.listUsersByRole(RoleConstant.REVIEWER, dto));
    }

    /**
     * 修改当前登录用户个人资料。
     */
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PutMapping("/profile")
    public R<Void> updateProfile(@Valid @RequestBody UserDTO dto) {
        userService.updateProfile(dto);
        return R.ok();
    }

    /**
     * 修改当前登录用户密码。
     */
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 5, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PutMapping("/password")
    public R<Void> updatePassword(@RequestBody Map<String, String> params) {
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        userService.updatePassword(oldPassword, newPassword);
        return R.ok();
    }
}
