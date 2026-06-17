package com.zwf.aisupport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginDTO {

    private String usernameOrEmail;

    // Kept for compatibility with older manual API payloads.
    private String username;

    @NotBlank(message = "Password cannot be blank")
    private String password;

    public String getLoginIdentifier() {
        if (usernameOrEmail != null && !usernameOrEmail.isBlank()) {
            return usernameOrEmail.trim();
        }

        return username == null ? null : username.trim();
    }
}
