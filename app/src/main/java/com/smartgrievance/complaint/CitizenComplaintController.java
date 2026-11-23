package com.smartgrievance.complaint;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.exception.StorageException;
import com.smartgrievance.common.web.ErrorReference;
import com.smartgrievance.common.web.Flash;
import com.smartgrievance.common.web.Forms;
import com.smartgrievance.config.AppProperties;
import com.smartgrievance.masterdata.LookupOption;
import com.smartgrievance.masterdata.MasterDataService;
import com.smartgrievance.user.CurrentUser;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

@Controller
public class CitizenComplaintController {
    private static final String CITIZEN = "/citizen/complaints";
    private static final String FORM = "citizen/complaint-form";
    private static final String ACTION = "complaint/action-form";
    private static final Logger LOG = LoggerFactory.getLogger(CitizenComplaintController.class);
    private final ComplaintService complaints;
    private final MasterDataService lookups;
    private final AppProperties.Upload uploads;
    private final Flash flash;

    public CitizenComplaintController(ComplaintService complaints, MasterDataService lookups,
                                      AppProperties properties, Flash flash) {
        this.complaints = complaints;
        this.lookups = lookups;
        this.uploads = properties.upload();
        this.flash = flash;
    }

    @GetMapping(CITIZEN + "/new")
    String create(Model model) {
        model.addAttribute("form", new ComplaintForm());
        return createPage(model);
    }

    @PostMapping(CITIZEN)
    String submit(@Valid @ModelAttribute("form") ComplaintForm form, BindingResult errors,
                  @RequestParam(name = "files", required = false) List<MultipartFile> files,
                  @AuthenticationPrincipal CurrentUser user, Model model) {
        if (!errors.hasErrors()) {
            try {
                return "redirect:" + CITIZEN + "/" + complaints.submit(form, present(files), user) + "/submitted";
            } catch (FormRejectedException rejection) {
                Forms.apply(rejection, errors);
            } catch (StorageException failure) {
                storageError(errors, user, failure);
            }
        }
        return createPage(model);
    }

    @GetMapping(CITIZEN + "/{no}/submitted")
    String submitted(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        model.addAttribute("complaint", complaints.submitted(no, user));
        return "citizen/submitted";
    }

    @GetMapping(CITIZEN + "/{no}/edit")
    String edit(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        var page = complaints.editPage(no, user);
        model.addAttribute("form", page.form());
        return editPage(model, page);
    }

    @PostMapping(CITIZEN + "/{no}/edit")
    String update(@PathVariable String no, @Valid @ModelAttribute("form") ComplaintEditForm form,
                  BindingResult errors, @RequestParam(name = "files", required = false) List<MultipartFile> files,
                  @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        var page = complaints.editPage(no, user);
        if (!errors.hasErrors()) {
            try {
                if (complaints.edit(no, form, present(files), user)) flash.success(redirect, "flash.complaint.updated");
                else flash.info(redirect, "flash.complaint.noChanges");
                return "redirect:/complaints/" + no;
            } catch (FormRejectedException rejection) {
                Forms.apply(rejection, errors);
            } catch (StorageException failure) {
                storageError(errors, user, failure);
            }
        }
        return editPage(model, page);
    }

    @GetMapping(CITIZEN + "/{no}/withdraw")
    String withdrawal(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        var complaint = complaints.withdrawal(no, user);
        var form = new RemarkForm();
        form.setVersion(complaint.version());
        model.addAttribute("form", form);
        return withdrawalPage(model, complaint);
    }

    @PostMapping(CITIZEN + "/{no}/withdraw")
    String withdraw(@PathVariable String no, @Valid @ModelAttribute("form") RemarkForm form, BindingResult errors,
                    @AuthenticationPrincipal CurrentUser user, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return withdrawalPage(model, complaints.withdrawal(no, user));
        complaints.withdraw(no, form, user);
        flash.success(redirect, "flash.complaint.withdrawn");
        return "redirect:/complaints/" + no;
    }

    @GetMapping("/complaints/{no}")
    String detail(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        var detail = complaints.detail(no, user);
        model.addAttribute("complaint", detail.complaint());
        model.addAttribute("currentAttachments", detail.files());
        model.addAttribute("canEdit", detail.canEdit());
        model.addAttribute("canWithdraw", detail.canWithdraw());
        return "complaint/submission-detail";
    }

    private String createPage(Model model) {
        formOptions(model, "CREATE", lookups.activeCategories(), lookups.activeAreas());
        return FORM;
    }

    private String editPage(Model model, ComplaintService.EditPage page) {
        model.addAttribute("complaint", page.complaint());
        model.addAttribute("currentAttachments", page.files());
        formOptions(model, "EDIT",
                withCurrent(lookups.activeCategories(), page.categoryId(), page.complaint().categoryName()),
                withCurrent(lookups.activeAreas(), page.areaId(), page.complaint().areaName()));
        return FORM;
    }

    private void formOptions(Model model, String mode, List<LookupOption> categories, List<LookupOption> areas) {
        model.addAttribute("mode", mode);
        model.addAttribute("categories", categories);
        model.addAttribute("areas", areas);
        model.addAttribute("maxFiles", uploads.maxFilesPerComplaint());
        model.addAttribute("maxFileSizeMb", uploads.maxFileSize().toMegabytes());
    }

    private static List<LookupOption> withCurrent(List<LookupOption> active, long id, String name) {
        if (active.stream().anyMatch(option -> option.id() == id)) return active;
        var options = new ArrayList<>(active);
        options.add(new LookupOption(id, name, false));
        return options;
    }

    private static String withdrawalPage(Model model, ComplaintSummary complaint) {
        model.addAttribute("complaint", complaint);
        model.addAttribute("action", "WITHDRAW");
        model.addAttribute("postUrl", CITIZEN + "/" + complaint.complaintNo() + "/withdraw");
        model.addAttribute("cancelUrl", "/complaints/" + complaint.complaintNo());
        return ACTION;
    }

    private static void storageError(BindingResult errors, CurrentUser user, StorageException failure) {
        String reference = ErrorReference.next();
        try (var ignored = MDC.putCloseable("ref", reference)) {
            LOG.error("STORAGE_FAILED ref={} userId={} purpose=COMPLAINT", reference, user.id(), failure);
        }
        errors.reject("validation.complaint.saveFailed", new Object[]{reference}, null);
    }

    private static List<MultipartFile> present(List<MultipartFile> files) { return files == null ? List.of() : files; }
}
