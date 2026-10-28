package com.zwf.aisupport.service;

import com.zwf.aisupport.entity.User;
import com.zwf.aisupport.vo.LoginVO;

import java.util.Map;

public interface OAuthAuthenticationService {
    User authenticateProvider(String provider, Map<String, Object> attributes);

    String createExchangeCode(Long userId);

    LoginVO exchange(String code);
}
