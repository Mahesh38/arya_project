package com.smartgrievance.auth;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.web.Flash;
import com.smartgrievance.common.web.Forms;
import com.smartgrievance.otp.OtpService;
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
public class RegistrationController {
    private final AccountService accounts;
    private final OtpService otp;
    private final AuthSessionService sessions;
    private final LoginAttemptService attempts;
    private final Flash flash;

    public RegistrationController(AccountService accounts, OtpService otp, AuthSessionService sessions,
                                  LoginAttemptService attempts, Flash flash) {
        this.accounts = accounts;
        this.otp = otp;
        this.sessions = sessions;
        this.attempts = attempts;
        this.flash = flash;
    }

    @GetMapping("/register")
    String register(@AuthenticationPrincipal CurrentUser user, Model model) {
        if (user != null) return "redirect:/";
        model.addAttribute("form", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    String register(@AuthenticationPrincipal CurrentUser user, @Valid @ModelAttribute("form") RegistrationForm form,
                    BindingResult errors, HttpServletRequest request, HttpServletResponse response,
                    RedirectAttributes redirect) {
        if (user != null) return "redirect:/";
        if (!attempts.allowRegistration(request.getRemoteAddr())) {
            errors.reject("validation.register.rateLimit");
            SecurityEvents.LOG.warn("REGISTER_LIMIT ip={}", request.getRemoteAddr());
        }
        if (errors.hasErrors()) return "auth/register";
        try {
            var account = accounts.register(form.getFullName(), form.getEmail(), form.getMobile(), form.getPassword());
            var issued = otp.issueOnRegistration(account.id());
            if (issued != null && issued.demoCode() != null) redirect.addFlashAttribute("demoOtp", issued.demoCode());
            sessions.login(account, request, response);
            SecurityEvents.LOG.info("REGISTERED userId={} ip={}", account.id(), request.getRemoteAddr());
            SecurityEvents.LOG.info("LOGIN_SUCCESS userId={} role={} ip={} method=registration",
                    account.id(), account.role(), request.getRemoteAddr());
            flash.success(redirect, "flash.registered");
            return "redirect:/verify-mobile";
        } catch (FormRejectedException ex) {
            Forms.apply(ex, errors);
            return "auth/register";
        }
    }
}
