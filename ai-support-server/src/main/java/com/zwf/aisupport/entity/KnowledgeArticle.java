package com.zwf.aisupport.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeArticle {

    private Long id;
    private String title;
    private String content;
    private String category;
    private Long createdBy;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
