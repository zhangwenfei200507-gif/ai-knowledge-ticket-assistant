package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.UserRegisterDTO;
import com.zwf.aisupport.entity.User;

public interface UserService {

    User findByUsername(String username);

    void register(UserRegisterDTO userRegisterDTO);
}