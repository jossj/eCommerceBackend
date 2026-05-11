package au.com.j2econsulting;

import au.com.j2econsulting.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ECommerceApplicationTests {

    @Test
    void contextLoads() {
    }
}
