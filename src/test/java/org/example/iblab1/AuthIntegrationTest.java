package org.example.iblab1;

import org.example.iblab1.model.dto.request.LoginRequest;
import org.example.iblab1.model.dto.request.RegistrationRequest;
import org.example.iblab1.model.entity.User;
import org.example.iblab1.repository.PostRepository;
import org.example.iblab1.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTransactionConfig.class)
class AuthIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PostRepository postRepository;

    private User existingUser(String login, String rawPassword) {
        User user = new User();
        user.setId(1);
        user.setLogin(login);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setIsActive(true);
        user.setRegistrationDate(OffsetDateTime.now());
        return user;
    }

    @Test
    void registrationStoresOnlyAPasswordHash() throws Exception {
        when(userRepository.existsByLogin(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/auth/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationRequest("alice", "SuperSecret123", "SuperSecret123"))))
                .andExpect(status().isCreated());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String storedHash = captor.getValue().getPasswordHash();

        assertNotEquals("SuperSecret123", storedHash);
        assertTrue(storedHash.startsWith("$2"), "password must be stored as a BCrypt hash");
        assertTrue(passwordEncoder.matches("SuperSecret123", storedHash));
    }

    @Test
    void registrationRejectsMismatchedPasswordConfirmation() throws Exception {
        mockMvc.perform(post("/auth/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationRequest("bob", "SuperSecret123", "OtherSecret123"))))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void registrationRejectsDuplicateLogin() throws Exception {
        when(userRepository.existsByLogin("alice")).thenReturn(true);

        mockMvc.perform(post("/auth/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationRequest("alice", "SuperSecret123", "SuperSecret123"))))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void registrationRejectsWeakPassword() throws Exception {
        mockMvc.perform(post("/auth/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationRequest("carol", "123", "123"))))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsJwtForValidCredentials() throws Exception {
        when(userRepository.findByLogin("alice")).thenReturn(Optional.of(existingUser("alice", "SuperSecret123")));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", "SuperSecret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.login").value("alice"));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        when(userRepository.findByLogin("alice")).thenReturn(Optional.of(existingUser("alice", "SuperSecret123")));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", "WrongPassword"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void loginRejectsUnknownUserWithTheSameMessageAsAWrongPassword() throws Exception {
        when(userRepository.findByLogin("mallory")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("mallory", "SuperSecret123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bad credentials"));
    }

    /**
     * A classic SQL injection payload must be treated as an ordinary string:
     * the ORM binds it as a parameter, so no user is ever found.
     */
    @Test
    void loginIsNotVulnerableToSqlInjection() throws Exception {
        String payload = "' OR '1'='1";
        when(userRepository.findByLogin(payload)).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(payload, "anything"))))
                .andExpect(status().isUnauthorized());

        verify(userRepository).findByLogin(payload);
    }
}
