package com.ecommerce.integration;

import com.ecommerce.config.TestSecurityConfig;
import com.ecommerce.dto.UserDTO;
import com.ecommerce.entity.User.Role;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
@Rollback
class UserIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void createUser_persistsAndReturnsCreatedUser() throws Exception {
        UserDTO dto = UserDTO.builder()
                .firstName("Alice").lastName("Smith")
                .email("alice@test.com").password("secret123")
                .phone("555-1234").address("1 Test Ave")
                .role(Role.CUSTOMER)
                .build();

        String response = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("alice@test.com"))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();

        UserDTO created = objectMapper.readValue(response, UserDTO.class);
        assertThat(userRepository.findById(created.getId())).isPresent();
    }

    @Test
    void createUser_duplicateEmail_returns409() throws Exception {
        UserDTO dto = UserDTO.builder()
                .firstName("Bob").lastName("Jones")
                .email("bob@test.com").password("password").build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("bob@test.com")));
    }

    @Test
    void getUserById_returnsExistingUser() throws Exception {
        UserDTO dto = UserDTO.builder()
                .firstName("Carol").lastName("White")
                .email("carol@test.com").password("pass").build();

        String response = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long userId = objectMapper.readValue(response, UserDTO.class).getId();

        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carol@test.com"));
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getUserByEmail_returnsUser() throws Exception {
        UserDTO dto = UserDTO.builder()
                .firstName("Dave").lastName("Black")
                .email("dave@test.com").password("pass").build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/email/dave@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Dave"));
    }

    @Test
    void getAllUsers_returnsAllCreatedUsers() throws Exception {
        for (int i = 1; i <= 3; i++) {
            UserDTO dto = UserDTO.builder()
                    .firstName("User" + i).lastName("Test")
                    .email("user" + i + "@test.com").password("pass").build();
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void updateUser_changesFieldsAndReturnsUpdated() throws Exception {
        UserDTO dto = UserDTO.builder()
                .firstName("Original").lastName("Name")
                .email("original@test.com").password("pass").build();

        String created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andReturn().getResponse().getContentAsString();
        Long userId = objectMapper.readValue(created, UserDTO.class).getId();

        UserDTO updateDTO = UserDTO.builder()
                .firstName("Updated").lastName("Name")
                .email("original@test.com").password("pass")
                .phone("999-9999").address("New Address").role(Role.ADMIN)
                .build();

        mockMvc.perform(put("/api/users/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.phone").value("999-9999"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void deleteUser_removesFromDatabase() throws Exception {
        UserDTO dto = UserDTO.builder()
                .firstName("ToDelete").lastName("User")
                .email("delete@test.com").password("pass").build();

        String created = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andReturn().getResponse().getContentAsString();
        Long userId = objectMapper.readValue(created, UserDTO.class).getId();

        mockMvc.perform(delete("/api/users/" + userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isNotFound());

        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    void createUser_invalidEmail_returns400WithValidationErrors() throws Exception {
        UserDTO invalid = UserDTO.builder()
                .firstName("Test").lastName("User")
                .email("not-an-email").password("pass").build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void createUser_defaultsRoleToCustomer_whenNotProvided() throws Exception {
        UserDTO dto = UserDTO.builder()
                .firstName("NoRole").lastName("User")
                .email("norole@test.com").password("pass").build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }
}
