package com.labelhub.modules.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("user_role")
public class UserRole implements Serializable {

    private Long userId;

    private Long roleId;
}
