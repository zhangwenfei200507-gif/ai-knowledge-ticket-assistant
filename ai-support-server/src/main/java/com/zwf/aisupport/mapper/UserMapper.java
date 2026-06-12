package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("select * from users where username = #{username}")
    User findByUsername(String username);

    @Insert("""
            insert into users (username, password_hash, display_name, email, role, status)
            values (#{username}, #{passwordHash}, #{displayName}, #{email}, #{role}, #{status})
            """)
    void insert(User user);
    @Select("select * from users where id = #{id}")
    User findById(Long id);
}