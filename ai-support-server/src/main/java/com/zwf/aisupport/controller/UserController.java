package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.UserRegisterDTO;
import com.zwf.aisupport.service.UserService;
import com.zwf.aisupport.vo.LoginVO;
import com.zwf.aisupport.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.zwf.aisupport.dto.UserLoginDTO;
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        UserVO user = userService.getCurrentUser();
        return Result.success(user);
    }
    @GetMapping("/{username}")
    public Result<UserVO> getByUsername(@PathVariable String username) {
        UserVO user = userService.findByUsername(username);
        return Result.success(user);
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO userRegisterDTO) {
        userService.register(userRegisterDTO);
        return Result.success();
    }
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody UserLoginDTO userLoginDTO) {
        LoginVO loginVO = userService.login(userLoginDTO);
        return Result.success(loginVO);
    }
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        userService.logout(token);
        return Result.success();
    }
}