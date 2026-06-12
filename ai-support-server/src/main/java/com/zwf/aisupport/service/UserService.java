package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.UserLoginDTO;
import com.zwf.aisupport.dto.UserRegisterDTO;
import com.zwf.aisupport.vo.UserVO;

public interface UserService {

    UserVO findByUsername(String username);

    void register(UserRegisterDTO userRegisterDTO);

    UserVO login(UserLoginDTO userLoginDTO);
}