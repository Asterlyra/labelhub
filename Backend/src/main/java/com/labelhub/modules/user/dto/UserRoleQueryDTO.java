package com.labelhub.modules.user.dto;

import lombok.Data;

@Data
public class UserRoleQueryDTO {

    private String nickname;

    private Integer status = 1;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
