package com.ecommerce.config;

import com.ecommerce.security.JwtAuthenticationFilter;
import com.ecommerce.security.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("#{'${cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Production security filter chain.
     *
     * @ConditionalOnMissingBean lets TestSecurityConfig register its own chain
     * first in test contexts, at which point this bean is skipped — so
     * JwtAuthenticationFilter (created inline below) is never instantiated in
     * tests, avoiding any dependency on beans unavailable in the web slice.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtUtil jwtUtil,
            UserDetailsService userDetailsService) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtUtil, userDetailsService);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // JWT uses the Authorization header, not cookies; CSRF is not applicable.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\","
                            + "\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"status\":403,\"error\":\"Forbidden\","
                            + "\"message\":\"Insufficient permissions\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // ── Public ───────────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()  // registration
                .requestMatchers(HttpMethod.POST, "/api/users").permitAll()          // registration (legacy)
                .requestMatchers(HttpMethod.GET,  "/api/products", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/images/**").permitAll()
                // ── Admin-only ───────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET,   "/api/users").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,  "/api/products").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,   "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/status").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/tracking").hasRole("ADMIN")
                // ── Authenticated ────────────────────────────────────────────
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
