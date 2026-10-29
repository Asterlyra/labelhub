package com.labelhub.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.user.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    @Select("SELECT r.role_key FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT p.perm_key FROM permission p JOIN role_permission rp ON p.id = rp.permission_id JOIN user_role ur ON rp.role_id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> selectPermissionKeysByUserId(@Param("userId") Long userId);
}
