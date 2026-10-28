package com.zwf.aisupport.security;

import com.zwf.aisupport.common.constant.RedisKeyConstant;
import com.zwf.aisupport.common.util.JwtUtil;
import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "USER");

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   StringRedisTemplate stringRedisTemplate,
                                   UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.userMapper = userMapper;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContextHolder.clearContext();
        String token = authorizationHeader.substring(7);
        try {
            Boolean tokenExists = stringRedisTemplate.hasKey(RedisKeyConstant.LOGIN_TOKEN_PREFIX + token);
            if (Boolean.TRUE.equals(tokenExists)) {
                Claims claims = jwtUtil.parseToken(token);
                Long userId = claims.get("userId", Long.class);
                User user = userId == null ? null : userMapper.findAuthenticationStateById(userId);
                Number tokenVersionClaim = claims.get("tokenVersion", Number.class);
                String role = user == null || user.getRole() == null
                        ? null
                        : user.getRole().trim().toUpperCase(Locale.ROOT);
                if (user != null
                        && tokenVersionClaim != null
                        && tokenVersionClaim.intValue() == (user.getTokenVersion() == null ? 0 : user.getTokenVersion())
                        && Integer.valueOf(1).equals(user.getStatus())
                        && ALLOWED_ROLES.contains(role)) {
                    CurrentUser currentUser = new CurrentUser(user.getId(), user.getUsername(), role);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    currentUser,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
