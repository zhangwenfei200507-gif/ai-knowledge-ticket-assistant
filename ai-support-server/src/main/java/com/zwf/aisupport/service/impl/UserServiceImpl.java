package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }
}