package org.example.iblab1.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationRequest {
    @NotBlank(message = "Login cannot be empty")
    @Size(min = 3, max = 50, message = "Login must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Login must not contain spaces or special characters")
    private String login;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(regexp = "^\\S+$", message = "Password must not contain whitespace")
    private String password;

    @NotBlank(message = "Password confirmation cannot be empty")
    private String passwordConfirmation;
}
