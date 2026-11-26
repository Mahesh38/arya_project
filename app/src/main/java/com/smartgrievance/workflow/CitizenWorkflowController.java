package com.smartgrievance.workflow;

import com.smartgrievance.complaint.RemarkForm;
import com.smartgrievance.user.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.smartgrievance.workflow.TransitionRules.Action.*;

@Controller
class CitizenWorkflowController {
    private static final String BASE = "/citizen/complaints/{no}/";
    private final WorkflowPages pages;

    CitizenWorkflowController(WorkflowPages pages) { this.pages = pages; }

    @GetMapping(BASE + "close")
    String closePage(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        return pages.get(no, CLOSE, BASE + "close", user, model);
    }

    @PostMapping(BASE + "close")
    String close(@PathVariable String no, @Valid @ModelAttribute("form") OptionalRemarkForm form, BindingResult errors,
            @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        return pages.post(no, CLOSE, BASE + "close", form, errors, List.of(), user, model, redirect);
    }

    @GetMapping(BASE + "reopen")
    String reopenPage(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        return pages.get(no, REOPEN, BASE + "reopen", user, model);
    }

    @PostMapping(BASE + "reopen")
    String reopen(@PathVariable String no, @Valid @ModelAttribute("form") RemarkForm form, BindingResult errors,
            @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        return pages.post(no, REOPEN, BASE + "reopen", form, errors, List.of(), user, model, redirect);
    }
}
