package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.vo.AdminUserVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("""
            select * from users
            where email is not null
              and lower(email) = lower(#{email})
            order by case when role = 'ADMIN' then 0 else 1 end, status desc, id
            limit 1
            """)
    User findByEmail(String email);

    @Insert("""
            insert into users (username, password_hash, display_name, email, role, status, token_version)
            values (#{username}, #{passwordHash}, #{displayName}, #{email}, #{role}, #{status}, #{tokenVersion})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertOAuthUser(User user);
    @Select("select * from users where id = #{id}")
    User findById(Long id);

    @Select("select id, username, role, status, token_version from users where id = #{id}")
    User findAuthenticationStateById(Long id);

    @Select("""
            select u.id, u.username, u.display_name, u.email, u.role, u.status,
                   u.created_time, u.updated_time,
                   coalesce(string_agg(distinct i.provider, ', ' order by i.provider), '') as linked_providers,
                   max(i.last_login_at) as last_login_time
            from users u
            left join user_oauth_identities i on i.user_id = u.id
            where (#{keyword} = '' or u.username ilike '%' || #{keyword} || '%'
                or coalesce(u.display_name, '') ilike '%' || #{keyword} || '%'
                or u.email ilike '%' || #{keyword} || '%')
              and (#{role} = '' or u.role = #{role})
              and (cast(#{status} as integer) is null or u.status = cast(#{status} as integer))
            group by u.id
            order by u.created_time desc, u.id desc
            limit #{limit} offset #{offset}
            """)
    List<AdminUserVO> findAdminUsers(@Param("keyword") String keyword,
                                     @Param("role") String role,
                                     @Param("status") Integer status,
                                     @Param("limit") int limit,
                                     @Param("offset") long offset);

    @Select("""
            select count(*) from users u
            where (#{keyword} = '' or u.username ilike '%' || #{keyword} || '%'
                or coalesce(u.display_name, '') ilike '%' || #{keyword} || '%'
                or u.email ilike '%' || #{keyword} || '%')
              and (#{role} = '' or u.role = #{role})
              and (cast(#{status} as integer) is null or u.status = cast(#{status} as integer))
            """)
    long countAdminUsers(@Param("keyword") String keyword,
                         @Param("role") String role,
                         @Param("status") Integer status);

    @Select("""
            select u.id, u.username, u.display_name, u.email, u.role, u.status,
                   u.created_time, u.updated_time,
                   coalesce(string_agg(distinct i.provider, ', ' order by i.provider), '') as linked_providers,
                   max(i.last_login_at) as last_login_time
            from users u
            left join user_oauth_identities i on i.user_id = u.id
            where u.id = #{id}
            group by u.id
            """)
    AdminUserVO findAdminUserById(Long id);

    @Select("select id from users where role = 'ADMIN' and status = 1 order by id for update")
    List<Long> lockActiveAdminUsers();

    @Select("select count(*) from users where role = 'ADMIN' and status = 1")
    long countActiveAdminUsers();

    @Update("""
            update users
            set role = 'ADMIN', token_version = coalesce(token_version, 0) + 1,
                updated_time = current_timestamp
            where id = #{id} and role is distinct from 'ADMIN'
            """)
    int promoteBootstrapAdmin(Long id);

    @Update("""
            update users
            set role = #{role}, status = #{status},
                token_version = coalesce(token_version, 0) + 1,
                updated_time = current_timestamp
            where id = #{id}
              and (role is distinct from #{role} or status is distinct from #{status})
            """)
    int updateAccess(@Param("id") Long id, @Param("role") String role, @Param("status") Integer status);
}
