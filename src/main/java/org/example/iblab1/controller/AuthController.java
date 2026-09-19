package org.example.iblab1.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.iblab1.model.dto.response.AuthResponse;
import org.example.iblab1.model.dto.response.ErrorMessageResponse;
import org.example.iblab1.model.dto.request.LoginRequest;
import org.example.iblab1.model.dto.request.RegistrationRequest;
import org.example.iblab1.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/registration")
    public ResponseEntity<ErrorMessageResponse> registration(@Valid @RequestBody RegistrationRequest registrationRequest) {
        authService.register(registrationRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ErrorMessageResponse("User registered successfully"));
    }
}
