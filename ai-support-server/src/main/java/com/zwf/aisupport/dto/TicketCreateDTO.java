package com.zwf.aisupport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TicketCreateDTO {

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;

    @NotBlank(message = "Description cannot be blank")
    private String description;

    @Size(max = 20, message = "Priority cannot exceed 20 characters")
    private String priority;
}