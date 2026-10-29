package com.labelhub.modules.user.convert;

import com.labelhub.modules.user.dto.RegisterDTO;
import com.labelhub.modules.user.dto.UserDTO;
import com.labelhub.modules.user.entity.User;
import com.labelhub.modules.user.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 用户模块对象转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserConvert {

    /**
     * 将注册请求转换为用户实体。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "roleName", ignore = true)
    User toEntity(RegisterDTO dto);

    /**
     * 将个人资料请求转换为用户实体。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "roleName", ignore = true)
    User toEntity(UserDTO dto);

    /**
     * 将用户实体转换为响应对象。
     */
    UserVO toVO(User user);
}
