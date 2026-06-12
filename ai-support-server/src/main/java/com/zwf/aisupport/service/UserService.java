package com.zwf.aisupport.service;

import com.zwf.aisupport.entity.User;

public interface UserService {

    User findByUsername(String username);
}