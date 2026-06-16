package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class DocumentSearchResultVO {

    private Long chunkId;
    private Long documentId;
    private String originalFilename;
    private String category;
    private Integer chunkIndex;
    private String content;
}