package au.com.j2econsulting.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    // 256-bit base64-encoded key — same value used in application-test.properties
    private static final String SECRET = "dGhpcy1pcy1hLXNlY3VyZS10ZXN0LWtleS1mb3Itand0";
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", EXPIRATION_MS);
    }

    @Test
    void generateToken_producesNonBlankToken() {
        String token = jwtUtil.generateToken("user@example.com", 1L, "CUSTOMER");
        assertThat(token).isNotBlank();
    }

    @Test
    void extractEmail_returnsSubject() {
        String token = jwtUtil.generateToken("alice@example.com", 42L, "ADMIN");
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("alice@example.com");
    }

    @Test
    void extractUserId_returnsCorrectId() {
        String token = jwtUtil.generateToken("bob@example.com", 99L, "CUSTOMER");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(99L);
    }

    @Test
    void extractAllClaims_containsRoleClaim() {
        String token = jwtUtil.generateToken("carol@example.com", 7L, "ADMIN");
        Claims claims = jwtUtil.extractAllClaims(token);
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void isTokenValid_trueForFreshToken() {
        String token = jwtUtil.generateToken("valid@example.com", 1L, "CUSTOMER");
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_falseForTamperedToken() {
        String token = jwtUtil.generateToken("user@example.com", 1L, "CUSTOMER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtUtil.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_falseForGarbage() {
        assertThat(jwtUtil.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    void isTokenValid_falseForExpiredToken() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1000L);
        String token = jwtUtil.generateToken("expired@example.com", 1L, "CUSTOMER");
        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }
}
