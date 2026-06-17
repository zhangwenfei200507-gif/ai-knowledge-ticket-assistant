package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.constant.RedisKeyConstant;
import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.JwtUtil;
import com.zwf.aisupport.dto.UserForgotPasswordDTO;
import com.zwf.aisupport.dto.UserLoginDTO;
import com.zwf.aisupport.dto.UserRegisterDTO;
import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.service.UserService;
import com.zwf.aisupport.vo.LoginVO;
import com.zwf.aisupport.vo.UserVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    public UserServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public UserVO findByUsername(String username) {
        User user = userMapper.findByUsername(username);
        return convertToUserVO(user);
    }

    @Override
    public void register(UserRegisterDTO userRegisterDTO) {
        if (!userRegisterDTO.getPassword().equals(userRegisterDTO.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        String email = userRegisterDTO.getEmail().trim();
        User existingEmailUser = userMapper.findByEmail(email);
        if (existingEmailUser != null) {
            throw new BusinessException("Email already exists");
        }

        String username = resolveRegistrationUsername(userRegisterDTO, email);
        User existingUser = userMapper.findByUsername(username);
        if (existingUser != null) {
            throw new BusinessException("Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        // Store only the encrypted password, never the raw password.
        user.setPasswordHash(passwordEncoder.encode(userRegisterDTO.getPassword()));
        user.setDisplayName(resolveDisplayName(userRegisterDTO, email));
        user.setEmail(email);
        user.setRole("USER");
        user.setStatus(1);

        userMapper.insert(user);
    }

    @Override
    public LoginVO login(UserLoginDTO userLoginDTO) {
        String identifier = userLoginDTO.getLoginIdentifier();
        if (identifier == null || identifier.isBlank()) {
            throw new BusinessException("Username or email cannot be blank");
        }

        User user = userMapper.findByUsernameOrEmail(identifier);
        if (user == null) {
            throw new BusinessException("Invalid username or password");
        }

        // Compare raw password from request with BCrypt hash stored in database.
        boolean passwordMatches = passwordEncoder.matches(userLoginDTO.getPassword(), user.getPasswordHash());
        if (!passwordMatches) {
            throw new BusinessException("Invalid username or password");
        }

        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());

// Store login token in Redis so the server can revoke it before JWT expiration.
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.LOGIN_TOKEN_PREFIX + token,
                String.valueOf(user.getId()),
                Duration.ofHours(2)
        );
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(convertToUserVO(user));
        return loginVO;
    }

    @Override
    public void resetPassword(UserForgotPasswordDTO forgotPasswordDTO) {
        if (!forgotPasswordDTO.getNewPassword().equals(forgotPasswordDTO.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        String identifier = forgotPasswordDTO.getUsernameOrEmail().trim();
        String registeredEmail = forgotPasswordDTO.getRegisteredEmail().trim();

        User user = userMapper.findByUsernameOrEmail(identifier);
        if (user == null || user.getEmail() == null || !user.getEmail().equalsIgnoreCase(registeredEmail)) {
            throw new BusinessException("Account information does not match");
        }

        userMapper.updatePassword(user.getId(), passwordEncoder.encode(forgotPasswordDTO.getNewPassword()));
    }

    private String resolveRegistrationUsername(UserRegisterDTO userRegisterDTO, String email) {
        if (userRegisterDTO.getUsername() != null && !userRegisterDTO.getUsername().isBlank()) {
            return userRegisterDTO.getUsername().trim();
        }

        String baseUsername = email.substring(0, email.indexOf("@"))
                .replaceAll("[^A-Za-z0-9_]", "_");

        if (baseUsername.length() < 3) {
            baseUsername = "user_" + baseUsername;
        }
        if (baseUsername.length() > 40) {
            baseUsername = baseUsername.substring(0, 40);
        }

        String candidate = baseUsername;
        int suffix = 1;
        while (userMapper.findByUsername(candidate) != null) {
            candidate = baseUsername + "_" + suffix;
            suffix++;
        }

        return candidate;
    }

    private String resolveDisplayName(UserRegisterDTO userRegisterDTO, String email) {
        if (userRegisterDTO.getDisplayName() != null && !userRegisterDTO.getDisplayName().isBlank()) {
            return userRegisterDTO.getDisplayName().trim();
        }

        return email.substring(0, email.indexOf("@"));
    }

    private UserVO convertToUserVO(User user) {
        if (user == null) {
            return null;
        }

        // Convert database entity to API response object to avoid exposing sensitive fields.
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setDisplayName(user.getDisplayName());
        userVO.setEmail(user.getEmail());
        userVO.setRole(user.getRole());
        userVO.setStatus(user.getStatus());
        userVO.setCreatedTime(user.getCreatedTime());
        userVO.setUpdatedTime(user.getUpdatedTime());

        return userVO;
    }
    @Override
    public UserVO getCurrentUser() {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        // Re-query the database so the API returns the latest user profile.
        User user = userMapper.findById(currentUserId);
        return convertToUserVO(user);
    }
    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        // Remove token from Redis so it can no longer access protected APIs.
        stringRedisTemplate.delete(RedisKeyConstant.LOGIN_TOKEN_PREFIX + token);
    }
}
