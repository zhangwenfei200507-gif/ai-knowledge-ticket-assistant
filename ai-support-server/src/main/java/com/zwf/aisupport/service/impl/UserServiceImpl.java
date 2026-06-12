package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.dto.UserRegisterDTO;
import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
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
}