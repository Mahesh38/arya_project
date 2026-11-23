package com.smartgrievance.auth;

import com.smartgrievance.user.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
class GlobalModelAdvice {
    @ModelAttribute
    void pageAttributes(@AuthenticationPrincipal CurrentUser user, HttpServletRequest request, Model model) {
        if (user == null) return;
        model.addAttribute("currentUser", user);
        model.addAttribute("homeUrl", user.role().home());
        model.addAttribute("unreadCount", 0);
        var session = request.getSession(false);
        if (session != null) model.addAttribute("sessionTimeoutSeconds", session.getMaxInactiveInterval());
        String path = request.getRequestURI().substring(request.getContextPath().length());
        model.addAttribute("activeMenu", switch (path) {
            case "/citizen/complaints" -> "my";
            case "/citizen/complaints/new" -> "new";
            case "/citizen/watching" -> "watching";
            case "/officer/queue" -> "queue";
            case "/admin/dashboard" -> "dashboard";
            case "/admin/complaints" -> "complaints";
            case "/notifications" -> "notifications";
            default -> "";
        });
    }
}
