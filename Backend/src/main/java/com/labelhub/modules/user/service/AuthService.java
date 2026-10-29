package com.labelhub.modules.user.service;

import com.labelhub.modules.user.dto.LoginDTO;
import com.labelhub.modules.user.vo.LoginVO;

public interface AuthService {

    /**
     * 用户登录
     */
    LoginVO login(LoginDTO dto);
}
