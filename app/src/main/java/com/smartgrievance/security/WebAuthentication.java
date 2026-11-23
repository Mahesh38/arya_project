package com.smartgrievance.security;

import java.io.IOException;
import java.net.URI;

import com.smartgrievance.user.Role;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.AppUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;

public final class WebAuthentication {
    public static final String LOGIN_EMAIL = "app.loginEmail";

    private WebAuthentication() {}

    public static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    public static boolean notificationCount(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && path(request).equals("/notifications/unread-count");
    }

    public static void unauthenticated(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthenticated\"}");
    }

    public static AuthenticationFailureHandler failureHandler() {
        return (request, response, failure) -> {
            String email = AppUserDetailsService.normalizeEmail(request.getParameter("email"));
            request.getSession().setAttribute(LOGIN_EMAIL, email.substring(0, Math.min(email.length(), 254)));
            boolean locked = failure instanceof LockedException;
            String reason = locked ? "locked" : failure instanceof DisabledException ? "inactive" : "bad_credentials";
            SecurityEvents.LOG.warn("LOGIN_FAILURE email={} ip={} reason={}",
                    SecurityEvents.maskEmail(email), request.getRemoteAddr(), reason);
            response.sendRedirect(request.getContextPath() + (locked ? "/login?locked" : "/login?error"));
        };
    }

    public static AuthenticationSuccessHandler successHandler(RequestCache cache) {
        return (request, response, authentication) -> {
            var user = (CurrentUser) authentication.getPrincipal();
            var session = request.getSession(false);
            if (session != null) session.removeAttribute(LOGIN_EMAIL);
            var saved = cache.getRequest(request, response);
            cache.removeRequest(request, response);
            String target = request.getContextPath() + user.role().home();
            if (saved != null) target = safeTarget(saved.getRedirectUrl(), request.getContextPath(), user.role(), target);
            SecurityEvents.LOG.info("LOGIN_SUCCESS userId={} role={} ip={} method=form",
                    user.id(), user.role(), request.getRemoteAddr());
            response.sendRedirect(target);
        };
    }

    public static void logout(HttpServletRequest request, HttpServletResponse response,
                              org.springframework.security.core.Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            SecurityEvents.LOG.info("LOGOUT userId={}", user.id());
        }
    }

    private static String safeTarget(String saved, String contextPath, Role role, String fallback) {
        try {
            var uri = URI.create(saved);
            var raw = uri.getRawPath();
            if (raw == null || !raw.startsWith(contextPath + "/") || raw.startsWith("//")) return fallback;
            String path = raw.substring(contextPath.length());
            if (path.startsWith("//") || path.contains("\\") || path.contains(";")) return fallback;
            boolean allowed = path.startsWith("/complaints/") || path.startsWith("/attachments/")
                    || path.startsWith("/account/") || path.equals("/notifications")
                    || switch (role) {
                        case CITIZEN -> path.startsWith("/citizen/") || path.equals("/verify-mobile");
                        case OFFICER -> path.startsWith("/officer/");
                        case ADMIN -> path.startsWith("/admin/");
                    };
            if (allowed) return raw + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (IllegalArgumentException ignored) {
            // An invalid saved URL falls back to the configured role home.
        }
        return fallback;
    }
}
