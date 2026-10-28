package com.zwf.aisupport.service;

import com.zwf.aisupport.vo.AdminUserVO;
import com.zwf.aisupport.vo.PageVO;

public interface AdminUserService {

    PageVO<AdminUserVO> findUsers(String keyword, String role, Integer status, int page, int size);

    AdminUserVO getUser(Long id);

    AdminUserVO updateAccess(Long id, String role, Integer status);
}
