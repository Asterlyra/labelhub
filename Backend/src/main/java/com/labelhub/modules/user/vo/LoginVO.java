package com.labelhub.modules.user.vo;

import lombok.Data;

import java.util.List;

@Data
public class LoginVO {

    private String satoken;

    private UserVO userInfo;

    private List<String> roles;
}
