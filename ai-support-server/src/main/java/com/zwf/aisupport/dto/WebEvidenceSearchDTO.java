package com.zwf.aisupport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WebEvidenceSearchDTO {

    @NotBlank(message = "Question cannot be blank")
    @Size(max = 300, message = "Question cannot exceed 300 characters")
    private String question;
}
