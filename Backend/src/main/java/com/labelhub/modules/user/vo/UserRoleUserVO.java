package com.labelhub.modules.user.vo;

import lombok.Data;

@Data
public class UserRoleUserVO {

    private Long userId;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    private Integer status;

    private String roleKey;

    private String roleName;
}
