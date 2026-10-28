package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.constant.RedisKeyConstant;
import com.zwf.aisupport.config.OAuthProperties;
import com.zwf.aisupport.entity.OAuthIdentity;
import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.mapper.OAuthIdentityMapper;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.common.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthAuthenticationServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private OAuthIdentityMapper identityMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    @Test
    void createsOrdinaryAccountForNewVerifiedProviderIdentity() {
        OAuthAuthenticationServiceImpl service = service();
        when(identityMapper.findByProviderSubject("google", "subject-1")).thenReturn(null);
        when(userMapper.findByEmail("operator@example.com")).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("opaque-password-hash");
        doAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(41L);
            return null;
        }).when(userMapper).insertOAuthUser(any(User.class));

        User user = service.authenticateProvider("google", Map.of(
                "sub", "subject-1",
                "email", "Operator@Example.com",
                "email_verified", true,
                "name", "Cloud Operator"
        ));

        assertThat(user.getId()).isEqualTo(41L);
        assertThat(user.getRole()).isEqualTo("USER");
        assertThat(user.getEmail()).isEqualTo("operator@example.com");
        ArgumentCaptor<OAuthIdentity> identity = ArgumentCaptor.forClass(OAuthIdentity.class);
        verify(identityMapper).insert(identity.capture());
        assertThat(identity.getValue().getProvider()).isEqualTo("google");
        assertThat(identity.getValue().getProviderSubject()).isEqualTo("subject-1");
    }

    @Test
    void createsConfiguredBootstrapEmailAsAdministrator() {
        OAuthProperties properties = new OAuthProperties();
        properties.setBootstrapAdminEmails("other@example.com, zhangwenfei200507@gmail.com");
        OAuthAuthenticationServiceImpl service = service(properties);
        when(identityMapper.findByProviderSubject("google", "subject-admin")).thenReturn(null);
        when(userMapper.findByEmail("zhangwenfei200507@gmail.com")).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("opaque-password-hash");
        doAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(42L);
            return null;
        }).when(userMapper).insertOAuthUser(any(User.class));

        User user = service.authenticateProvider("google", Map.of(
                "sub", "subject-admin",
                "email", "ZhangWenfei200507@gmail.com",
                "email_verified", true,
                "name", "Platform Admin"
        ));

        assertThat(user.getRole()).isEqualTo("ADMIN");
        assertThat(user.getEmail()).isEqualTo("zhangwenfei200507@gmail.com");
    }

    @Test
    void promotesExistingConfiguredVerifiedAccountAndReloadsItsRevokedSessionState() {
        OAuthProperties properties = new OAuthProperties();
        properties.setBootstrapAdminEmails("zhangwenfei200507@gmail.com");
        OAuthAuthenticationServiceImpl service = service(properties);
        User existing = new User();
        existing.setId(43L);
        existing.setEmail("zhangwenfei200507@gmail.com");
        existing.setRole("USER");
        existing.setStatus(1);
        User promoted = new User();
        promoted.setId(43L);
        promoted.setEmail("zhangwenfei200507@gmail.com");
        promoted.setRole("ADMIN");
        promoted.setStatus(1);
        when(identityMapper.findByProviderSubject("google", "subject-admin")).thenReturn(null);
        when(userMapper.findByEmail("zhangwenfei200507@gmail.com")).thenReturn(existing);
        when(userMapper.findById(43L)).thenReturn(promoted);
        when(userMapper.promoteBootstrapAdmin(43L)).thenReturn(1);

        User result = service.authenticateProvider("google", Map.of(
                "sub", "subject-admin",
                "email", "zhangwenfei200507@gmail.com",
                "email_verified", true
        ));

        assertThat(result.getRole()).isEqualTo("ADMIN");
        verify(userMapper).promoteBootstrapAdmin(43L);
        verify(userMapper).findById(43L);
    }

    @Test
    void linksVerifiedEmailToExistingAdminWithoutChangingRole() {
        OAuthAuthenticationServiceImpl service = service();
        User admin = new User();
        admin.setId(9L);
        admin.setEmail("admin@example.com");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        when(identityMapper.findByProviderSubject("github", "22")).thenReturn(null);
        when(userMapper.findByEmail("admin@example.com")).thenReturn(admin);

        User result = service.authenticateProvider("github", Map.of(
                "id", 22,
                "email", "admin@example.com",
                "email_verified", true,
                "login", "admin"
        ));

        assertThat(result.getRole()).isEqualTo("ADMIN");
        verify(userMapper, org.mockito.Mockito.never()).insertOAuthUser(any());
        verify(identityMapper).insert(any(OAuthIdentity.class));
    }

    @Test
    void refusesAnUnverifiedEmailBeforeLookingUpAnAccount() {
        OAuthAuthenticationServiceImpl service = service();

        assertThatThrownBy(() -> service.authenticateProvider("google", Map.of(
                "sub", "subject-1", "email", "operator@example.com", "email_verified", false)))
                .hasMessage("A verified provider identity is required");

        verify(identityMapper, org.mockito.Mockito.never()).findByProviderSubject(any(), any());
    }

    @Test
    void exchangeConsumesOneTimeCodeAndCreatesRevocableJwtSession() {
        OAuthAuthenticationServiceImpl service = service();
        User user = new User();
        user.setId(6L);
        user.setUsername("oauth_user");
        user.setEmail("user@example.com");
        user.setRole("USER");
        user.setStatus(1);
        user.setTokenVersion(2);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(RedisKeyConstant.OAUTH_EXCHANGE_PREFIX + "a".repeat(43)))
                .thenReturn("6");
        when(userMapper.findById(6L)).thenReturn(user);
        when(jwtUtil.createToken(6L, "oauth_user", "USER", 2)).thenReturn("jwt");

        var login = service.exchange("a".repeat(43));

        assertThat(login.getToken()).isEqualTo("jwt");
        assertThat(login.getUser().getEmail()).isEqualTo("user@example.com");
        verify(values).set(eq(RedisKeyConstant.LOGIN_TOKEN_PREFIX + "jwt"), eq("6"), any(Duration.class));
    }

    @Test
    void capsExchangeCodeLifetimeEvenWhenMisconfigured() {
        OAuthProperties properties = new OAuthProperties();
        properties.setExchangeCodeTtlSeconds(Long.MAX_VALUE);
        when(redis.opsForValue()).thenReturn(values);
        OAuthAuthenticationServiceImpl service = new OAuthAuthenticationServiceImpl(
                userMapper, identityMapper, passwordEncoder, jwtUtil, redis, properties, 7_200_000);

        String code = service.createExchangeCode(6L);

        assertThat(code).hasSize(43);
        verify(values).set(eq(RedisKeyConstant.OAUTH_EXCHANGE_PREFIX + code), eq("6"), eq(Duration.ofSeconds(180)));
    }

    private OAuthAuthenticationServiceImpl service() {
        return service(new OAuthProperties());
    }

    private OAuthAuthenticationServiceImpl service(OAuthProperties properties) {
        return new OAuthAuthenticationServiceImpl(
                userMapper, identityMapper, passwordEncoder, jwtUtil, redis, properties, 7_200_000);
    }
}
