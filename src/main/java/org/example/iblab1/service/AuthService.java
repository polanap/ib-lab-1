package org.example.iblab1.service;

import lombok.RequiredArgsConstructor;
import org.example.iblab1.model.dto.response.AuthResponse;
import org.example.iblab1.model.dto.request.LoginRequest;
import org.example.iblab1.model.dto.request.RegistrationRequest;
import org.example.iblab1.model.entity.User;
import org.example.iblab1.repository.UserRepository;
import org.example.iblab1.security.CustomUserDetails;
import org.example.iblab1.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getLogin(), loginRequest.getPassword())
        );

        String jwt = tokenProvider.generateToken(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        return AuthResponse.builder()
                .token(jwt)
                .expiresIn(tokenProvider.getJwtExpirationMs())
                .userId(user.getId())
                .login(user.getLogin())
                .build();
    }

    public void register(RegistrationRequest registrationRequest) {
        if (!registrationRequest.getPassword().equals(registrationRequest.getPasswordConfirmation())) {
            throw new IllegalArgumentException("Password confirmation does not match the password");
        }

        if (userRepository.existsByLogin(registrationRequest.getLogin())) {
            throw new IllegalArgumentException("Login is already taken");
        }

        User user = new User();
        user.setLogin(registrationRequest.getLogin());
        user.setPasswordHash(passwordEncoder.encode(registrationRequest.getPassword()));
        user.setIsActive(true);

        userRepository.save(user);
    }
}
