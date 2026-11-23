package com.smartgrievance.auth;

import java.util.Map;

import com.smartgrievance.config.AppProperties;
import com.smartgrievance.security.WebAuthentication;
import com.smartgrievance.user.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {
    private final AppProperties props;

    public LoginController(AppProperties props) {
        this.props = props;
    }

    public record LoginForm(String email) {}

    @GetMapping("/")
    String home(@AuthenticationPrincipal CurrentUser user) {
        return "redirect:" + (user == null ? "/login" : user.role().home());
    }

    @GetMapping("/login")
    String login(@AuthenticationPrincipal CurrentUser user, HttpServletRequest request, Model model) {
        if (user != null) return "redirect:/";
        String email = "";
        var session = request.getSession(false);
        if (session != null && session.getAttribute(WebAuthentication.LOGIN_EMAIL) instanceof String typed) {
            email = typed;
            session.removeAttribute(WebAuthentication.LOGIN_EMAIL);
        }
        model.addAttribute("form", new LoginForm(email));
        model.addAttribute("loginError", request.getParameter("error") != null);
        model.addAttribute("accountLocked", request.getParameter("locked") != null);
        model.addAttribute("loggedOut", request.getParameter("logout") != null);
        model.addAttribute("sessionExpired", request.getParameter("expired") != null);
        model.addAttribute("accountInactive", request.getParameter("inactive") != null);
        model.addAttribute("lockMinutes", props.security().login().lock().toMinutes());
        return "auth/login";
    }

    @GetMapping({"/citizen/complaints", "/officer/queue", "/admin/dashboard"})
    String roleHome(@AuthenticationPrincipal CurrentUser user, Model model) {
        model.addAttribute("pageTitle", user.role().title());
        return "auth/role-home";
    }

    @GetMapping("/notifications/unread-count")
    @ResponseBody
    Map<String, Integer> sessionActivity(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Map.of("unread", 0);
    }
}
