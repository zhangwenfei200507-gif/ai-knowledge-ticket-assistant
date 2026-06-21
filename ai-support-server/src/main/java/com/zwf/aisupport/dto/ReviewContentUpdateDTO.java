package com.zwf.aisupport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewContentUpdateDTO {

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 180, message = "Title cannot exceed 180 characters")
    private String title;

    @NotBlank(message = "Content cannot be blank")
    private String content;

    @Size(max = 50, message = "Category cannot exceed 50 characters")
    private String category;
}
