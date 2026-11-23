package com.smartgrievance.testsupport;

import com.smartgrievance.user.AppUserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class WithAppUserFactory implements WithSecurityContextFactory<WithAppUser> {
    private final AppUserDetailsService users;

    @Autowired
    public WithAppUserFactory(AppUserDetailsService users) {
        this.users = users;
    }

    @Override
    public SecurityContext createSecurityContext(WithAppUser annotation) {
        var user = users.loadUserByUsername(annotation.value());
        user.eraseCredentials();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        return context;
    }
}
