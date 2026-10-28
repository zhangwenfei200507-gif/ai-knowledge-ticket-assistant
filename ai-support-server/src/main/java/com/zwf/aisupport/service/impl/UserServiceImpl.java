package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.constant.RedisKeyConstant;
import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.service.UserService;
import com.zwf.aisupport.vo.UserVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    public UserServiceImpl(UserMapper userMapper, StringRedisTemplate redis) {
        this.userMapper = userMapper;
        this.redis = redis;
    }

    @Override
    public UserVO getCurrentUser() {
        Long userId = CurrentUserUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("User is not authenticated");
        }
        return toUserVO(userMapper.findById(userId));
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(RedisKeyConstant.LOGIN_TOKEN_PREFIX + token);
        }
    }

    private UserVO toUserVO(User user) {
        if (user == null) {
            throw new BusinessException("User account is unavailable");
        }
        UserVO result = new UserVO();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setDisplayName(user.getDisplayName());
        result.setEmail(user.getEmail());
        result.setRole(user.getRole());
        result.setStatus(user.getStatus());
        result.setCreatedTime(user.getCreatedTime());
        result.setUpdatedTime(user.getUpdatedTime());
        return result;
    }
}
