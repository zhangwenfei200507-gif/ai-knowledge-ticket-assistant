package com.zwf.aisupport.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {

    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String role;
    private Integer status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}