package com.zwf.aisupport.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserVO {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String role;
    private Integer status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String linkedProviders;
    private LocalDateTime lastLoginTime;
    private boolean bootstrapAdmin;
}
