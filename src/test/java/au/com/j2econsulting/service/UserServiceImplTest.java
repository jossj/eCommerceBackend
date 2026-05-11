package au.com.j2econsulting.service;

import au.com.j2econsulting.dto.UserDTO;
import au.com.j2econsulting.entity.User;
import au.com.j2econsulting.exception.ResourceAlreadyExistsException;
import au.com.j2econsulting.exception.ResourceNotFoundException;
import au.com.j2econsulting.repository.UserRepository;
import au.com.j2econsulting.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserServiceImpl userService;

    private User user;
    private UserDTO userDTO;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("hashed-password")
                .phone("1234567890")
                .address("123 Main St")
                .role(User.Role.CUSTOMER)
                .build();

        userDTO = UserDTO.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("plaintext")
                .phone("1234567890")
                .address("123 Main St")
                .role(User.Role.CUSTOMER)
                .build();
    }

    @Test
    void createUser_success() {
        when(userRepository.existsByEmail(userDTO.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserDTO result = userService.createUser(userDTO);

        assertThat(result.getEmail()).isEqualTo("john@example.com");
        assertThat(result.getFirstName()).isEqualTo("John");
        verify(passwordEncoder).encode("plaintext");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_duplicateEmail_throwsException() {
        when(userRepository.existsByEmail(userDTO.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(userDTO))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("john@example.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_alwaysSetsRoleToCustomer() {
        userDTO.setRole(User.Role.ADMIN);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            assertThat(saved.getRole()).isEqualTo(User.Role.CUSTOMER);
            return user;
        });

        userService.createUser(userDTO);
    }

    @Test
    void getUserById_found() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserDTO result = userService.getUserById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void getUserById_notFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getUserByEmail_found() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        UserDTO result = userService.getUserByEmail("john@example.com");

        assertThat(result.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void getUserByEmail_notFound_throwsException() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByEmail("nobody@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nobody@example.com");
    }

    @Test
    void getAllUsers_returnsMappedList() {
        User second = User.builder().id(2L).firstName("Jane").lastName("Doe")
                .email("jane@example.com").password("pw").role(User.Role.ADMIN).build();
        when(userRepository.findAll()).thenReturn(List.of(user, second));

        List<UserDTO> result = userService.getAllUsers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserDTO::getEmail)
                .containsExactly("john@example.com", "jane@example.com");
    }

    @Test
    void getAllUsers_emptyRepo_returnsEmptyList() {
        when(userRepository.findAll()).thenReturn(List.of());
        assertThat(userService.getAllUsers()).isEmpty();
    }

    @Test
    void updateUser_success() {
        UserDTO updateDTO = UserDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phone("9876543210")
                .address("456 Elm St")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.updateUser(1L, updateDTO);

        assertThat(user.getFirstName()).isEqualTo("Jane");
        assertThat(user.getLastName()).isEqualTo("Smith");
        assertThat(user.getPhone()).isEqualTo("9876543210");
        assertThat(user.getAddress()).isEqualTo("456 Elm St");
        // Role must not change via updateUser
        assertThat(user.getRole()).isEqualTo(User.Role.CUSTOMER);
        verify(userRepository).save(user);
    }

    @Test
    void updateUser_roleIgnored_evenWhenSupplied() {
        UserDTO updateDTO = UserDTO.builder()
                .firstName("Jane").lastName("Smith").role(User.Role.ADMIN).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.updateUser(1L, updateDTO);

        assertThat(user.getRole()).isEqualTo(User.Role.CUSTOMER);
    }

    @Test
    void updateUser_notFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(99L, userDTO))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deleteUser_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doNothing().when(userRepository).deleteById(1L);

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    void deleteUser_notFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(userRepository, never()).deleteById(any());
    }
}
