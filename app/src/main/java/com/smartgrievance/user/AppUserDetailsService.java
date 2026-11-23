package com.smartgrievance.user;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService, UserDetailsPasswordService {
    private final UserRepository users;
    private final Clock clock;

    public AppUserDetailsService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public CurrentUser loadUserByUsername(String email) {
        return users.byEmail(normalizeEmail(email)).map(CurrentUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Account unavailable"));
    }

    @Override
    public UserDetails updatePassword(UserDetails details, String encodedPassword) {
        var user = (CurrentUser) details;
        if (users.updateHash(user.id(), user.version(), encodedPassword, LocalDateTime.now(clock))) {
            return loadUserByUsername(user.getUsername());
        }
        return user;
    }
}
