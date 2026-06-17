package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.JwtUtil;
import com.zwf.aisupport.dto.UserForgotPasswordDTO;
import com.zwf.aisupport.dto.UserLoginDTO;
import com.zwf.aisupport.dto.UserRegisterDTO;
import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.vo.LoginVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void registerCreatesUserFromEmailAccount() {
        UserRegisterDTO registerDTO = new UserRegisterDTO();
        registerDTO.setEmail("ops.engineer@example.com");
        registerDTO.setDisplayName("Ops Engineer");
        registerDTO.setPassword("secure123");
        registerDTO.setConfirmPassword("secure123");

        when(userMapper.findByEmail("ops.engineer@example.com")).thenReturn(null);
        when(userMapper.findByUsername("ops_engineer")).thenReturn(null);
        when(passwordEncoder.encode("secure123")).thenReturn("encoded-password");

        userService.register(registerDTO);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());

        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getUsername()).isEqualTo("ops_engineer");
        assertThat(createdUser.getEmail()).isEqualTo("ops.engineer@example.com");
        assertThat(createdUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(createdUser.getDisplayName()).isEqualTo("Ops Engineer");
        assertThat(createdUser.getRole()).isEqualTo("USER");
        assertThat(createdUser.getStatus()).isEqualTo(1);
    }

    @Test
    void loginAcceptsUsernameOrEmailIdentifier() {
        UserLoginDTO loginDTO = new UserLoginDTO();
        loginDTO.setUsernameOrEmail("ops.engineer@example.com");
        loginDTO.setPassword("secure123");

        User user = new User();
        user.setId(7L);
        user.setUsername("ops_engineer");
        user.setEmail("ops.engineer@example.com");
        user.setRole("USER");
        user.setPasswordHash("encoded-password");

        when(userMapper.findByUsernameOrEmail("ops.engineer@example.com")).thenReturn(user);
        when(passwordEncoder.matches("secure123", "encoded-password")).thenReturn(true);
        when(jwtUtil.createToken(7L, "ops_engineer", "USER")).thenReturn("jwt-token");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        LoginVO loginVO = userService.login(loginDTO);

        assertThat(loginVO.getToken()).isEqualTo("jwt-token");
        assertThat(loginVO.getUser().getEmail()).isEqualTo("ops.engineer@example.com");
        verify(valueOperations).set(eq("login:token:jwt-token"), eq("7"), any(Duration.class));
    }

    @Test
    void resetPasswordUpdatesPasswordWhenEmailMatches() {
        UserForgotPasswordDTO forgotPasswordDTO = new UserForgotPasswordDTO();
        forgotPasswordDTO.setUsernameOrEmail("ops.engineer@example.com");
        forgotPasswordDTO.setRegisteredEmail("ops.engineer@example.com");
        forgotPasswordDTO.setNewPassword("newSecure123");
        forgotPasswordDTO.setConfirmPassword("newSecure123");

        User user = new User();
        user.setId(7L);
        user.setEmail("ops.engineer@example.com");

        when(userMapper.findByUsernameOrEmail("ops.engineer@example.com")).thenReturn(user);
        when(passwordEncoder.encode("newSecure123")).thenReturn("new-encoded-password");

        userService.resetPassword(forgotPasswordDTO);

        verify(userMapper).updatePassword(7L, "new-encoded-password");
    }

    @Test
    void resetPasswordRejectsMismatchedEmail() {
        UserForgotPasswordDTO forgotPasswordDTO = new UserForgotPasswordDTO();
        forgotPasswordDTO.setUsernameOrEmail("ops.engineer@example.com");
        forgotPasswordDTO.setRegisteredEmail("other@example.com");
        forgotPasswordDTO.setNewPassword("newSecure123");
        forgotPasswordDTO.setConfirmPassword("newSecure123");

        User user = new User();
        user.setId(7L);
        user.setEmail("ops.engineer@example.com");

        when(userMapper.findByUsernameOrEmail("ops.engineer@example.com")).thenReturn(user);

        assertThatThrownBy(() -> userService.resetPassword(forgotPasswordDTO))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account information does not match");

        verify(userMapper, never()).updatePassword(any(), any());
    }
}
