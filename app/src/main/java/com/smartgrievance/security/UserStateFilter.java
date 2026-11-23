package com.smartgrievance.security;

import java.io.IOException;

import com.smartgrievance.user.CurrentAccountService;
import com.smartgrievance.user.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class UserStateFilter extends OncePerRequestFilter {
    private final CurrentAccountService accounts;
    private final AuthSessionService sessions;

    public UserStateFilter(CurrentAccountService accounts, AuthSessionService sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = WebAuthentication.path(request);
        return path.equals("/error") || path.equals("/favicon.ico") || path.startsWith("/webjars/")
                || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            var account = accounts.find(user.id()).orElse(null);
            boolean active = account != null && account.active();
            if (!active || !user.fingerprint().equals(CurrentUser.fingerprint(account.passwordHash()))) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                SecurityEvents.sessionEnded(user.id(), active ? "password_changed" : "inactive");
                if (WebAuthentication.notificationCount(request)) WebAuthentication.unauthenticated(response);
                else response.sendRedirect(request.getContextPath() + (active ? "/login?expired" : "/login?inactive"));
                return;
            }
            if (user.version() != account.version()) sessions.refresh(account, request, response);
        }
        chain.doFilter(request, response);
    }
}
