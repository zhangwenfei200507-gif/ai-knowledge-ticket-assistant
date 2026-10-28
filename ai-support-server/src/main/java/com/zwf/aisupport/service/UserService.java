package com.zwf.aisupport.service;

import com.zwf.aisupport.vo.UserVO;

public interface UserService {

    UserVO getCurrentUser();

    void logout(String token);

}
