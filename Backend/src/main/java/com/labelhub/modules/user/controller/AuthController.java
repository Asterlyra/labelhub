package com.labelhub.modules.user.controller;

import com.labelhub.common.annotation.RateLimit;
import com.labelhub.common.result.R;
import com.labelhub.modules.user.dto.LoginDTO;
import com.labelhub.modules.user.dto.RegisterDTO;
import com.labelhub.modules.user.service.AuthService;
import com.labelhub.modules.user.service.UserService;
import com.labelhub.modules.user.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /**
     * 用户登录。
     */
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 300, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.IP, count = 5, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /**
     * 用户注册。
     */
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 60, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.IP, count = 2, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return R.ok();
    }
}
