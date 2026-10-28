package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.OAuthIdentity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OAuthIdentityMapper {

    @Select("""
            select id, user_id, provider, provider_subject
            from user_oauth_identities
            where provider = #{provider} and provider_subject = #{subject}
            """)
    OAuthIdentity findByProviderSubject(@Param("provider") String provider,
                                        @Param("subject") String subject);

    @Insert("""
            insert into user_oauth_identities (user_id, provider, provider_subject)
            values (#{userId}, #{provider}, #{providerSubject})
            """)
    void insert(OAuthIdentity identity);

    @Update("""
            update user_oauth_identities
            set last_login_at = current_timestamp
            where id = #{id}
            """)
    void touchLastLogin(Long id);
}
