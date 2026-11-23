package com.smartgrievance.security;

import com.smartgrievance.user.Account;
import com.smartgrievance.user.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessionStrategy;

    public AuthSessionService(SecurityContextRepository contexts, SessionAuthenticationStrategy sessionStrategy) {
        this.contexts = contexts;
        this.sessionStrategy = sessionStrategy;
    }

    public void login(Account account, HttpServletRequest request, HttpServletResponse response) {
        save(account, request, response, true);
    }

    public void refresh(Account account, HttpServletRequest request, HttpServletResponse response) {
        save(account, request, response, false);
    }

    private void save(Account account, HttpServletRequest request, HttpServletResponse response, boolean newLogin) {
        var user = new CurrentUser(account);
        user.eraseCredentials();
        var token = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        token.setDetails(new WebAuthenticationDetails(request));
        if (newLogin) sessionStrategy.onAuthentication(token, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }
}
