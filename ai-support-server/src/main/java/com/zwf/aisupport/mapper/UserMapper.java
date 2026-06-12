package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("select * from users where username = #{username}")
    User findByUsername(String username);
}