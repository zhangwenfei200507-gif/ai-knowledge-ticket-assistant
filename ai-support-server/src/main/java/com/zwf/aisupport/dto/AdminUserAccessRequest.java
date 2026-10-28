package com.zwf.aisupport.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminUserAccessRequest {

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "(?i)ADMIN|USER", message = "Role must be ADMIN or USER")
    private String role;

    @NotNull(message = "Account status is required")
    @Min(value = 0, message = "Status must be 0 or 1")
    @Max(value = 1, message = "Status must be 0 or 1")
    private Integer status;
}
