package com.labelhub.modules.user.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.user.entity.User;
import com.labelhub.modules.user.vo.UserRoleUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            <script>
            SELECT
                u.id AS userId,
                u.username,
                u.nickname,
                u.email,
                u.phone,
                u.avatar,
                u.status,
                r.role_key AS roleKey,
                r.role_name AS roleName
            FROM user u
            INNER JOIN user_role ur ON u.id = ur.user_id
            INNER JOIN role r ON ur.role_id = r.id
            WHERE r.role_key = #{roleKey}
              AND u.deleted = 0
            <if test="nickname != null and nickname != ''">
              AND u.nickname LIKE CONCAT('%', #{nickname}, '%')
            </if>
            <if test="status != null">
              AND u.status = #{status}
            </if>
            ORDER BY u.create_time DESC
            </script>
            """)
    Page<UserRoleUserVO> selectUsersByRole(Page<UserRoleUserVO> page,
                                           @Param("roleKey") String roleKey,
                                           @Param("nickname") String nickname,
                                           @Param("status") Integer status);
}
