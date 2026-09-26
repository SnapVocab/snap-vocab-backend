package vn.ptit.snapvocab.user.domain.mapper;

import vn.ptit.snapvocab.common.mapper.EntityMapper;

import org.mapstruct.Mapper;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.domain.dto.user.UserDTO;

@Mapper(componentModel = "spring")
public interface UserMapper extends EntityMapper<UserDTO, User> {

}

