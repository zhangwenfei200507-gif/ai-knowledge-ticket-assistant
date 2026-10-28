package com.zwf.aisupport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuthExchangeDTO {
    @NotBlank(message = "OAuth exchange code is required")
    private String code;
}
