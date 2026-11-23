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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.smartgrievance.workflow.TransitionRules.Action.*;

@Controller
class OfficerWorkflowController {
    private static final String BASE = "/officer/complaints/{no}/";
    private final WorkflowPages pages;

    OfficerWorkflowController(WorkflowPages pages) { this.pages = pages; }

    @GetMapping(BASE + "start")
    String startPage(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        return pages.get(no, START_WORK, BASE + "start", user, model);
    }

    @PostMapping(BASE + "start")
    String start(@PathVariable String no, @Valid @ModelAttribute("form") RemarkForm form, BindingResult errors,
            @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        return pages.post(no, START_WORK, BASE + "start", form, errors, List.of(), user, model, redirect);
    }

    @GetMapping(BASE + "remark")
    String remarkPage(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        return pages.get(no, ADD_REMARK, BASE + "remark", user, model);
    }

    @PostMapping(BASE + "remark")
    String remark(@PathVariable String no, @Valid @ModelAttribute("form") RemarkForm form, BindingResult errors,
            @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        return pages.post(no, ADD_REMARK, BASE + "remark", form, errors, List.of(), user, model, redirect);
    }

    @GetMapping(BASE + "resolve")
    String resolvePage(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        return pages.get(no, RESOLVE, BASE + "resolve", user, model);
    }

    @PostMapping(BASE + "resolve")
    String resolve(@PathVariable String no, @Valid @ModelAttribute("form") ResolveForm form, BindingResult errors,
            @RequestParam(name = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        return pages.post(no, RESOLVE, BASE + "resolve", form, errors, files, user, model, redirect);
    }

    @GetMapping(BASE + "reject")
    String rejectPage(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        return pages.get(no, REJECT, BASE + "reject", user, model);
    }

    @PostMapping(BASE + "reject")
    String reject(@PathVariable String no, @Valid @ModelAttribute("form") RemarkForm form, BindingResult errors,
            @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        return pages.post(no, REJECT, BASE + "reject", form, errors, List.of(), user, model, redirect);
    }

    @GetMapping(BASE + "send-back")
    String sendBackPage(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        return pages.get(no, SEND_BACK, BASE + "send-back", user, model);
    }

    @PostMapping(BASE + "send-back")
    String sendBack(@PathVariable String no, @Valid @ModelAttribute("form") RemarkForm form, BindingResult errors,
            @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        return pages.post(no, SEND_BACK, BASE + "send-back", form, errors, List.of(), user, model, redirect);
    }
}
