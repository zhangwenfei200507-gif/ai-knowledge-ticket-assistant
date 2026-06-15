package com.zwf.aisupport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiAskDTO {

    @NotBlank(message = "Question cannot be blank")
    private String question;
}