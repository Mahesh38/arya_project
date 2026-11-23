package com.smartgrievance.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

public class AppAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure)
            throws IOException {
        if (failure instanceof CsrfException) {
            String login = request.getContextPath() + "/login";
            if (request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) {
                response.sendRedirect(login + "?expired");
                return;
            }
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (request.getSession(false) == null || authentication == null || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                response.sendRedirect(login);
                return;
            }
            request.setAttribute("app.errorReason", "csrf");
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
