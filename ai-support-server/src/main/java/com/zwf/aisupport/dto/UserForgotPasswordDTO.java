package com.zwf.aisupport.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserForgotPasswordDTO {

    @NotBlank(message = "Username or email cannot be blank")
    private String usernameOrEmail;

    @NotBlank(message = "Registered email cannot be blank")
    @Email(message = "Invalid email format")
    private String registeredEmail;

    @NotBlank(message = "New password cannot be blank")
    @Size(min = 6, max = 100, message = "Password length must be between 6 and 100")
    private String newPassword;

    @NotBlank(message = "Confirm password cannot be blank")
    private String confirmPassword;
}
