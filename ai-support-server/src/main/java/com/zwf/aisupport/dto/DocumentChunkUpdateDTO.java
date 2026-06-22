package com.zwf.aisupport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentChunkUpdateDTO {

    @NotBlank(message = "Chunk content cannot be blank")
    private String content;
}
