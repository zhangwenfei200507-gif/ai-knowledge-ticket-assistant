package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.constant.RedisKeyConstant;
import com.zwf.aisupport.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private StringRedisTemplate redis;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void logoutRevokesOnlyTheCurrentSessionToken() {
        userService.logout("signed-token");

        verify(redis).delete(RedisKeyConstant.LOGIN_TOKEN_PREFIX + "signed-token");
    }
}
