package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.AdminUserAccessRequest;
import com.zwf.aisupport.service.AdminUserService;
import com.zwf.aisupport.vo.AdminUserVO;
import com.zwf.aisupport.vo.PageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public Result<PageVO<AdminUserVO>> findUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminUserService.findUsers(keyword, role, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<AdminUserVO> getUser(@PathVariable Long id) {
        return Result.success(adminUserService.getUser(id));
    }

    @PatchMapping("/{id}/access")
    public Result<AdminUserVO> updateAccess(@PathVariable Long id,
                                            @Valid @RequestBody AdminUserAccessRequest request) {
        return Result.success(adminUserService.updateAccess(id, request.getRole(), request.getStatus()));
    }
}
