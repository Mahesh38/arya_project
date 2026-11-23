package com.smartgrievance.duplicate;

import com.smartgrievance.common.exception.BusinessRuleException;
import com.smartgrievance.common.web.Flash;
import com.smartgrievance.complaint.ComplaintSummary;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.Role;
import com.smartgrievance.workflow.TransitionRules.Action;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class DuplicateController {
    private static final String OFFICER = "/officer/complaints/{no}/duplicate";
    private static final String ADMIN = "/admin/complaints/{no}/duplicate";
    private static final Pattern NUMBER = Pattern.compile(DuplicateForm.NUMBER_PATTERN);
    private final DuplicateService duplicates;
    private final Flash flash;

    DuplicateController(DuplicateService duplicates, Flash flash) {
        this.duplicates = duplicates;
        this.flash = flash;
    }

    @GetMapping({OFFICER, ADMIN})
    String preview(@PathVariable String no, @RequestParam(required = false) String original,
                   HttpServletRequest request, @AuthenticationPrincipal CurrentUser actor, Model model) {
        var complaint = duplicates.actionPage(no, actor);
        var form = new DuplicateForm();
        form.setVersion(complaint.version());
        form.setOriginalNo(original);
        var binding = new BeanPropertyBindingResult(form, "form");
        ComplaintSummary candidate = null;
        if (original != null && NUMBER.matcher(original).matches()) {
            try {
                candidate = duplicates.preview(no, original, actor);
            } catch (BusinessRuleException refusal) {
                binding.rejectValue("originalNo", refusal.messageKey(), refusal.args(), null);
            }
        } else if (request.getParameterMap().containsKey("original")) {
            binding.rejectValue("originalNo", original == null || original.isBlank()
                    ? "validation.originalNo.required" : "validation.originalNo.format");
        }
        model.addAllAttributes(binding.getModel());
        return page(model, complaint, candidate, candidate != null, actor);
    }

    @PostMapping({OFFICER, ADMIN})
    String link(@PathVariable String no, @Valid @ModelAttribute("form") DuplicateForm form, BindingResult binding,
                @AuthenticationPrincipal CurrentUser actor, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            var complaint = duplicates.actionPage(no, actor);
            var original = binding.hasFieldErrors("originalNo") ? null : duplicates.preview(no, form.getOriginalNo(), actor);
            return page(model, complaint, original, true, actor);
        }
        duplicates.markDuplicate(no, form.getOriginalNo(), form.getVersion(), form.getRemark(), actor);
        flash.success(redirect, "flash.complaint.duplicateLinked", form.getOriginalNo());
        return "redirect:/complaints/" + no;
    }

    private static String page(Model model, ComplaintSummary complaint, ComplaintSummary original,
                               boolean confirm, CurrentUser actor) {
        var path = actor.role() == Role.ADMIN ? ADMIN : OFFICER;
        model.addAttribute("action", Action.MARK_DUPLICATE);
        model.addAttribute("complaint", complaint);
        model.addAttribute("original", original);
        model.addAttribute("duplicateConfirm", confirm);
        model.addAttribute("postUrl", path.replace("{no}", complaint.complaintNo()));
        model.addAttribute("cancelUrl", "/complaints/" + complaint.complaintNo());
        return "complaint/action-form";
    }
}
