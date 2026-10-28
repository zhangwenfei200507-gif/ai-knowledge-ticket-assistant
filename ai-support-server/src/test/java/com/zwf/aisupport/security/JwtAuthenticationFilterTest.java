package com.zwf.aisupport.security;

import com.zwf.aisupport.common.constant.RedisKeyConstant;
import com.zwf.aisupport.common.util.JwtUtil;
import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, redis, userMapper);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void databaseRoleOverridesStaleJwtRole() throws Exception {
        Claims claims = mock(Claims.class);
        when(redis.hasKey(RedisKeyConstant.LOGIN_TOKEN_PREFIX + "active-token")).thenReturn(true);
        when(jwtUtil.parseToken("active-token")).thenReturn(claims);
        when(claims.get("userId", Long.class)).thenReturn(7L);
        when(claims.get("tokenVersion", Number.class)).thenReturn(0);
        when(userMapper.findAuthenticationStateById(7L)).thenReturn(activeUser("alice", "USER"));

        AtomicReference<Authentication> authentication = new AtomicReference<>();
        filter.doFilter(request("active-token"), new MockHttpServletResponse(), capture(authentication));

        assertThat(authentication.get().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        assertThat(((CurrentUser) authentication.get().getPrincipal()).getUsername()).isEqualTo("alice");
        verify(claims, never()).get("role", String.class);
    }

    @Test
    void disabledUserIsNotAuthenticatedEvenWhenTokenRemainsInRedis() throws Exception {
        Claims claims = mock(Claims.class);
        User user = activeUser("alice", "ADMIN");
        user.setStatus(0);
        when(redis.hasKey(RedisKeyConstant.LOGIN_TOKEN_PREFIX + "active-token")).thenReturn(true);
        when(jwtUtil.parseToken("active-token")).thenReturn(claims);
        when(claims.get("userId", Long.class)).thenReturn(7L);
        when(claims.get("tokenVersion", Number.class)).thenReturn(0);
        when(userMapper.findAuthenticationStateById(7L)).thenReturn(user);

        AtomicReference<Authentication> authentication = new AtomicReference<>();
        filter.doFilter(request("active-token"), new MockHttpServletResponse(), capture(authentication));

        assertThat(authentication.get()).isNull();
    }

    @Test
    void revokedTokenDoesNotQueryUser() throws Exception {
        when(redis.hasKey(RedisKeyConstant.LOGIN_TOKEN_PREFIX + "revoked-token")).thenReturn(false);
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request("revoked-token"), new MockHttpServletResponse(), capture(authentication));

        assertThat(authentication.get()).isNull();
        verify(userMapper, never()).findAuthenticationStateById(7L);
    }

    @Test
    void tokenFromPreviousPasswordVersionIsRejected() throws Exception {
        Claims claims = mock(Claims.class);
        User user = activeUser("alice", "USER");
        user.setTokenVersion(1);
        when(redis.hasKey(RedisKeyConstant.LOGIN_TOKEN_PREFIX + "active-token")).thenReturn(true);
        when(jwtUtil.parseToken("active-token")).thenReturn(claims);
        when(claims.get("userId", Long.class)).thenReturn(7L);
        when(claims.get("tokenVersion", Number.class)).thenReturn(0);
        when(userMapper.findAuthenticationStateById(7L)).thenReturn(user);
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request("active-token"), new MockHttpServletResponse(), capture(authentication));

        assertThat(authentication.get()).isNull();
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private FilterChain capture(AtomicReference<Authentication> authentication) {
        return (request, response) -> authentication.set(SecurityContextHolder.getContext().getAuthentication());
    }

    private User activeUser(String username, String role) {
        User user = new User();
        user.setId(7L);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus(1);
        user.setTokenVersion(0);
        return user;
    }
}
