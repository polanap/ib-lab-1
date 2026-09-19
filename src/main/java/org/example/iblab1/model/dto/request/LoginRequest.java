package org.example.iblab1.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Login cannot be empty")
    @Size(max = 50, message = "Login must not be longer than 50 characters")
    private String login;

    @NotBlank(message = "Password cannot be empty")
    @Size(max = 72, message = "Password must not be longer than 72 characters")
    private String password;
}
