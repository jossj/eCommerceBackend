package com.ecommerce.integration;

import com.ecommerce.config.TestSecurityConfig;
import com.ecommerce.dto.LoginRequest;
import com.ecommerce.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
@Rollback
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    private void registerUser(String email, String password) throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Test", "lastName", "User",
                                "email", email, "password", password))))
                .andExpect(status().isCreated());
    }

    @Test
    void login_withRegisteredUser_returnsJwtToken() throws Exception {
        registerUser("login@test.com", "password123");

        LoginRequest request = LoginRequest.builder()
                .email("login@test.com").password("password123").build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.email").value("login@test.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void login_wrongPassword_returns401WithGenericMessage() throws Exception {
        registerUser("user@test.com", "correctPassword");

        LoginRequest request = LoginRequest.builder()
                .email("user@test.com").password("wrongPassword").build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_unknownEmail_returns401WithGenericMessage() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("nobody@test.com").password("anyPassword").build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // ── register ────────────────────────────────────────────────────────────

    @Test
    void register_newUser_returns201WithTokenAndPersistsUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Jane", "lastName", "Doe",
                                "email", "jane@test.com", "password", "secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.email").value("jane@test.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.userId").isNumber());

        assertThat(userRepository.existsByEmail("jane@test.com")).isTrue();
    }

    @Test
    void register_roleIsAlwaysCustomer() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Bad", "lastName", "Actor",
                                "email", "badactor@test.com", "password", "pass",
                                "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "First", "lastName", "User",
                                "email", "dup@test.com", "password", "pass1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Second", "lastName", "User",
                                "email", "dup@test.com", "password", "pass2"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User already exists with email: dup@test.com"));
    }

    @Test
    void register_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Jane", "lastName", "Doe",
                                "email", "jane@test.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Jane", "lastName", "Doe",
                                "email", "not-an-email", "password", "secret123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void register_thenLogin_succeeds() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Jane", "lastName", "Doe",
                                "email", "jane.login@test.com", "password", "mypassword"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("jane.login@test.com").password("mypassword").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.email").value("jane.login@test.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }
}
