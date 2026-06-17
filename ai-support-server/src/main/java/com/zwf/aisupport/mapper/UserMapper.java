package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("select * from users where username = #{username}")
    User findByUsername(String username);

    @Select("""
            select * from users
            where email is not null
              and lower(email) = lower(#{email})
            """)
    User findByEmail(String email);

    @Select("""
            select * from users
            where username = #{identifier}
               or (email is not null and lower(email) = lower(#{identifier}))
            """)
    User findByUsernameOrEmail(String identifier);

    @Insert("""
            insert into users (username, password_hash, display_name, email, role, status)
            values (#{username}, #{passwordHash}, #{displayName}, #{email}, #{role}, #{status})
            """)
    void insert(User user);
    @Select("select * from users where id = #{id}")
    User findById(Long id);

    @Update("""
            update users
            set password_hash = #{passwordHash},
                updated_time = CURRENT_TIMESTAMP
            where id = #{id}
            """)
    void updatePassword(@Param("id") Long id,
                        @Param("passwordHash") String passwordHash);
}
