package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.JwtUtil;
import com.zwf.aisupport.dto.UserLoginDTO;
import com.zwf.aisupport.dto.UserRegisterDTO;
import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.service.UserService;
import com.zwf.aisupport.vo.LoginVO;
import com.zwf.aisupport.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder,JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public UserVO findByUsername(String username) {
        User user = userMapper.findByUsername(username);
        return convertToUserVO(user);
    }

    @Override
    public void register(UserRegisterDTO userRegisterDTO) {
        User existingUser = userMapper.findByUsername(userRegisterDTO.getUsername());
        if (existingUser != null) {
            throw new BusinessException("Username already exists");
        }

        User user = new User();
        user.setUsername(userRegisterDTO.getUsername());
        // Store only the encrypted password, never the raw password.
        user.setPasswordHash(passwordEncoder.encode(userRegisterDTO.getPassword()));
        user.setDisplayName(userRegisterDTO.getDisplayName());
        user.setEmail(userRegisterDTO.getEmail());
        user.setRole("USER");
        user.setStatus(1);

        userMapper.insert(user);
    }

    @Override
    public LoginVO login(UserLoginDTO userLoginDTO) {
        User user = userMapper.findByUsername(userLoginDTO.getUsername());
        if (user == null) {
            throw new BusinessException("Invalid username or password");
        }

        // Compare raw password from request with BCrypt hash stored in database.
        boolean passwordMatches = passwordEncoder.matches(userLoginDTO.getPassword(), user.getPasswordHash());
        if (!passwordMatches) {
            throw new BusinessException("Invalid username or password");
        }

        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(convertToUserVO(user));
        return loginVO;
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
}