package com.smartgrievance.auth;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.web.Flash;
import com.smartgrievance.common.web.Forms;
import com.smartgrievance.security.AuthSessionService;
import com.smartgrievance.security.LoginAttemptService;
import com.smartgrievance.security.SecurityEvents;
import com.smartgrievance.user.AccountService;
import com.smartgrievance.user.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordController {
    private final AccountService accounts;
    private final AuthSessionService sessions;
    private final LoginAttemptService attempts;
    private final Flash flash;

    public PasswordController(AccountService accounts, AuthSessionService sessions, LoginAttemptService attempts, Flash flash) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.attempts = attempts;
        this.flash = flash;
    }

    @GetMapping("/account/password")
    String password(Model model) {
        model.addAttribute("form", new ChangePasswordForm(null, null, null));
        return "auth/change-password";
    }

    @PostMapping("/account/password")
    String password(@AuthenticationPrincipal CurrentUser user,
                    @Valid @ModelAttribute("form") ChangePasswordForm form, BindingResult errors,
                    HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        if (errors.hasErrors()) return "auth/change-password";
        try {
            var changed = accounts.changePassword(user.id(), form.currentPassword(), form.newPassword());
            request.changeSessionId();
            sessions.refresh(changed, request, response);
            attempts.clearAccount(user.getUsername());
            SecurityEvents.LOG.info("PASSWORD_CHANGED userId={}", user.id());
            flash.success(redirect, "flash.password.changed");
            return "redirect:/account/password";
        } catch (FormRejectedException ex) {
            Forms.apply(ex, errors);
            return "auth/change-password";
        }
    }
}
