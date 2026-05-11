package au.com.j2econsulting.security;

import au.com.j2econsulting.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * UserDetails implementation that carries the user's database ID and role,
 * making them available to controllers via @AuthenticationPrincipal.
 */
@Getter
public class AuthenticatedUser implements UserDetails {

    private final Long userId;
    private final String email;
    private final String password;
    private final User.Role role;

    public AuthenticatedUser(User user) {
        this.userId = user.getId();
        this.email  = user.getEmail();
        this.password = user.getPassword();
        this.role   = user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
}
