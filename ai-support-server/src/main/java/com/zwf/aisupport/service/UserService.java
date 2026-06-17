package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.UserLoginDTO;
import com.zwf.aisupport.dto.UserForgotPasswordDTO;
import com.zwf.aisupport.dto.UserRegisterDTO;
import com.zwf.aisupport.vo.LoginVO;
import com.zwf.aisupport.vo.UserVO;

public interface UserService {

    UserVO findByUsername(String username);

    void register(UserRegisterDTO userRegisterDTO);

    LoginVO login(UserLoginDTO userLoginDTO);

    UserVO getCurrentUser();

    void logout(String token);

    void resetPassword(UserForgotPasswordDTO forgotPasswordDTO);

}
