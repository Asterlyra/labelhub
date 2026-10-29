package com.labelhub.modules.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.exception.ErrorCode;
import com.labelhub.modules.user.convert.UserConvert;
import com.labelhub.modules.user.dto.LoginDTO;
import com.labelhub.modules.user.entity.User;
import com.labelhub.modules.user.service.AuthService;
import com.labelhub.modules.user.service.UserService;
import com.labelhub.modules.user.vo.LoginVO;
import com.labelhub.modules.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserConvert userConvert;

    @Override
    public LoginVO login(LoginDTO dto) {
        log.info("用户登录开始：{}", dto.getUsername());
        try {
            User user = userService.findByUsername(dto.getUsername());
            if (user == null) {
                log.warn("用户登录失败，用户名不存在：{}", dto.getUsername());
                throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR);
            }

            if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
                log.warn("用户登录失败，密码不匹配：{}", dto.getUsername());
                throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR);
            }

            if (user.getStatus() == null || user.getStatus() != 1) {
                log.warn("用户登录失败，账号已禁用：{}", dto.getUsername());
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            }

            StpUtil.login(user.getId());

            UserVO userVO = userConvert.toVO(user);
            List<String> roles = userService.getUserRoles(user.getId());

            LoginVO loginVO = new LoginVO();
            loginVO.setSatoken(StpUtil.getTokenValue());
            loginVO.setUserInfo(userVO);
            loginVO.setRoles(roles);
            log.info("用户登录成功：{}，角色：{}", dto.getUsername(), roles);
            return loginVO;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("用户登录异常：{}", e.getMessage(), e);
            throw new BusinessException("用户登录失败：" + e.getMessage());
        }
    }
}
