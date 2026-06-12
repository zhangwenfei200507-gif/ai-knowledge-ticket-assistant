package com.zwf.aisupport.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Ticket {

    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private Long createdBy;
    private Long assignedTo;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}