package com.smartgrievance.security;

import java.nio.charset.StandardCharsets;

import com.smartgrievance.user.AppUserDetailsService;
import com.smartgrievance.user.CurrentUser;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Component
public class ThrottledAuthenticationProvider implements AuthenticationProvider {
    private final AppUserDetailsService users;
    private final PasswordEncoder encoder;
    private final LoginAttemptService attempts;
    private final String unusedHash;

    public ThrottledAuthenticationProvider(AppUserDetailsService users, PasswordEncoder encoder,
                                           LoginAttemptService attempts) {
        this.users = users;
        this.encoder = encoder;
        this.attempts = attempts;
        unusedHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Override
    public Authentication authenticate(Authentication request) {
        String email = AppUserDetailsService.normalizeEmail(request.getName());
        if (email.length() > 254) email = email.substring(0, 254);
        String ip = request.getDetails() instanceof WebAuthenticationDetails details ? details.getRemoteAddress() : "";
        if (attempts.locked(email, ip)) throw new LockedException("Login temporarily unavailable");
        CurrentUser user;
        try {
            user = users.loadUserByUsername(email);
        } catch (UsernameNotFoundException ex) {
            user = null;
        }
        String candidate = request.getCredentials() instanceof String value ? value : "";
        boolean tooLong = candidate.getBytes(StandardCharsets.UTF_8).length > 72;
        boolean matches = encoder.matches(tooLong ? "" : candidate, user == null ? unusedHash : user.getPassword());
        if (!matches || tooLong || user == null) {
            attempts.failed(email, ip);
            throw new BadCredentialsException("Login unavailable");
        }
        if (!user.isEnabled()) throw new DisabledException("Login unavailable");
        if (encoder.upgradeEncoding(user.getPassword())) {
            user = (CurrentUser) users.updatePassword(user, encoder.encode(candidate));
        }
        attempts.clearAccount(email);
        var authenticated = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        authenticated.setDetails(request.getDetails());
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> tokenType) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tokenType);
    }
}
