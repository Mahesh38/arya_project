package com.smartgrievance.otp;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.web.Flash;
import com.smartgrievance.common.web.Forms;
import com.smartgrievance.security.AuthSessionService;
import com.smartgrievance.user.CurrentAccountService;
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
public class VerificationController {
    private static final String VIEW = "auth/verify-mobile";
    private static final String REDIRECT = "redirect:/verify-mobile";
    private static final String CITIZEN_HOME = "redirect:/citizen/complaints";

    private final OtpService codes;
    private final CurrentAccountService accounts;
    private final AuthSessionService sessions;
    private final Flash flash;

    public VerificationController(OtpService codes, CurrentAccountService accounts, AuthSessionService sessions, Flash flash) {
        this.codes = codes;
        this.accounts = accounts;
        this.sessions = sessions;
        this.flash = flash;
    }

    @GetMapping("/verify-mobile")
    String verify(@AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        var status = codes.status(user.id(), true);
        if (status == null) return alreadyVerified(redirect);
        model.addAttribute("form", new OtpForm());
        model.addAttribute("otp", status);
        return VIEW;
    }

    @PostMapping("/verify-mobile")
    String verify(@AuthenticationPrincipal CurrentUser user, @Valid @ModelAttribute("form") OtpForm form,
                  BindingResult errors, Model model, HttpServletRequest request, HttpServletResponse response,
                  RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                if (!codes.verify(user.id(), form.getCode())) return alreadyVerified(redirect);
                sessions.refresh(accounts.find(user.id()).orElseThrow(), request, response);
                flash.success(redirect, "flash.otp.verified");
                return CITIZEN_HOME;
            } catch (FormRejectedException ex) {
                Forms.apply(ex, errors);
            }
        }
        var status = codes.status(user.id(), false);
        if (status == null) return alreadyVerified(redirect);
        model.addAttribute("otp", status);
        return VIEW;
    }

    @PostMapping("/verify-mobile/resend")
    String resend(@AuthenticationPrincipal CurrentUser user, RedirectAttributes redirect) {
        try {
            var status = codes.resend(user.id());
            if (status == null) return alreadyVerified(redirect);
            if (status.demoCode() != null) redirect.addFlashAttribute("demoOtp", status.demoCode());
            flash.success(redirect, "flash.otp.sent", status.maskedMobile());
        } catch (FormRejectedException ex) {
            var error = ex.errors().getFirst();
            flash.error(redirect, error.key(), error.args());
        }
        return REDIRECT;
    }

    private String alreadyVerified(RedirectAttributes redirect) {
        flash.info(redirect, "flash.otp.alreadyVerified");
        return CITIZEN_HOME;
    }
}
