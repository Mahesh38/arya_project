package com.smartgrievance.security;

import java.io.IOException;

import com.smartgrievance.user.CurrentUser;
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
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var user = authentication != null && authentication.getPrincipal() instanceof CurrentUser current
                ? current : null;
        String path = WebAuthentication.path(request);
        if (failure instanceof CsrfException) {
            String login = request.getContextPath() + "/login";
            if (request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) {
                logCsrf(user, path, "expired");
                response.sendRedirect(login + "?expired");
                return;
            }
            boolean authenticated = authentication != null && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken);
            if (request.getRequestedSessionId() == null && !authenticated) {
                logCsrf(user, path, "login");
                response.sendRedirect(login);
                return;
            }
            logCsrf(user, path, "403");
            request.setAttribute("app.errorReason", "csrf");
        } else if (user != null) {
            SecurityEvents.LOG.warn("ACCESS_DENIED userId={} role={} method={} path={}",
                    user.id(), user.role(), request.getMethod(), path);
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    private static void logCsrf(CurrentUser user, String path, String outcome) {
        SecurityEvents.LOG.warn("CSRF_REJECTED userId={} path={} outcome={}",
                user == null ? "anonymous" : user.id(), path, outcome);
    }
}
