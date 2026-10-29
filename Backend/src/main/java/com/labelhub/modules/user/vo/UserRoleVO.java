package com.labelhub.modules.user.vo;

import lombok.Data;

import java.util.List;

/**
 * 当前用户角色身份响应对象。
 */
@Data
public class UserRoleVO {

    private Long userId;

    private List<String> roles;

}
