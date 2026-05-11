package au.com.j2econsulting.controller;

import au.com.j2econsulting.dto.AuthResponse;
import au.com.j2econsulting.dto.LoginRequest;
import au.com.j2econsulting.dto.RegisterRequest;
import au.com.j2econsulting.dto.UserDTO;
import au.com.j2econsulting.security.AuthenticatedUser;
import au.com.j2econsulting.security.JwtUtil;
import au.com.j2econsulting.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
        String token = jwtUtil.generateToken(
                principal.getEmail(), principal.getUserId(), principal.getRole().name());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(principal.getUserId())
                .email(principal.getEmail())
                .role(principal.getRole().name())
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserDTO userDTO = UserDTO.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(request.getPassword())
                .phone(request.getPhone())
                .address(request.getAddress())
                .build();

        UserDTO created = userService.createUser(userDTO);

        String token = jwtUtil.generateToken(
                created.getEmail(), created.getId(), created.getRole().name());

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(created.getId())
                .email(created.getEmail())
                .role(created.getRole().name())
                .build());
    }
}
