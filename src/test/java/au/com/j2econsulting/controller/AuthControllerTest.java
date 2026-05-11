package au.com.j2econsulting.controller;

import au.com.j2econsulting.config.TestSecurityConfig;
import au.com.j2econsulting.dto.LoginRequest;
import au.com.j2econsulting.dto.RegisterRequest;
import au.com.j2econsulting.dto.UserDTO;
import au.com.j2econsulting.entity.User;
import au.com.j2econsulting.exception.ResourceAlreadyExistsException;
import au.com.j2econsulting.security.AuthenticatedUser;
import au.com.j2econsulting.security.JwtUtil;
import au.com.j2econsulting.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthenticationManager authenticationManager;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserService userService;

    @Test
    void login_validCredentials_returnsTokenAndUserInfo() throws Exception {
        User user = User.builder()
                .id(1L).email("alice@example.com").password("hashed")
                .firstName("Alice").lastName("Admin").role(User.Role.ADMIN).build();
        AuthenticatedUser principal = new AuthenticatedUser(user);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authToken);
        when(jwtUtil.generateToken("alice@example.com", 1L, "ADMIN")).thenReturn("mock-jwt-token");

        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com").password("admin1234").build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = LoginRequest.builder()
                .email("nobody@example.com").password("wrong").build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_missingEmail_returns400() throws Exception {
        LoginRequest request = LoginRequest.builder().password("pass").build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("not-an-email").password("pass").build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com").build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    // ── register ────────────────────────────────────────────────────────────

    @Test
    void register_validRequest_returns201WithToken() throws Exception {
        UserDTO created = UserDTO.builder()
                .id(5L).email("jane@example.com")
                .firstName("Jane").lastName("Doe")
                .role(User.Role.CUSTOMER).build();

        when(userService.createUser(any())).thenReturn(created);
        when(jwtUtil.generateToken("jane@example.com", 5L, "CUSTOMER")).thenReturn("new-jwt-token");

        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane").lastName("Doe")
                .email("jane@example.com").password("secret123").build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("new-jwt-token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        when(userService.createUser(any()))
                .thenThrow(new ResourceAlreadyExistsException("User already exists with email: jane@example.com"));

        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane").lastName("Doe")
                .email("jane@example.com").password("secret123").build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User already exists with email: jane@example.com"));
    }

    @Test
    void register_missingFirstName_returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .lastName("Doe").email("jane@example.com").password("secret123").build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void register_missingPassword_returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane").lastName("Doe").email("jane@example.com").build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void register_invalidEmailFormat_returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane").lastName("Doe")
                .email("not-an-email").password("secret123").build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").isArray());
    }
}
