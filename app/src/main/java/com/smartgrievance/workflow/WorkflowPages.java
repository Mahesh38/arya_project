package com.smartgrievance.workflow;

import com.smartgrievance.attachment.FileStorageService;
import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.exception.StorageException;
import com.smartgrievance.common.web.ErrorReference;
import com.smartgrievance.common.web.Flash;
import com.smartgrievance.common.web.Forms;
import com.smartgrievance.complaint.RemarkForm;
import com.smartgrievance.config.AppProperties;
import com.smartgrievance.masterdata.MasterDataService;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Component
class WorkflowPages {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowPages.class);
    private final ComplaintWorkflowService workflow;
    private final MasterDataService lookups;
    private final Flash flash;
    private final long maxFileSizeMb;

    WorkflowPages(ComplaintWorkflowService workflow, MasterDataService lookups, Flash flash, AppProperties properties) {
        this.workflow = workflow;
        this.lookups = lookups;
        this.flash = flash;
        this.maxFileSizeMb = properties.upload().maxFileSize().toMegabytes();
    }

    String get(String no, Action action, String path, CurrentUser user, Model model) {
        var page = workflow.actionPage(no, action, user);
        VersionForm form = switch (action) {
            case ASSIGN -> {
                var assign = new AssignForm();
                assign.setDepartmentId(page.defaultDepartmentId());
                yield assign;
            }
            case REASSIGN -> new ReassignForm();
            case CLOSE -> new OptionalRemarkForm();
            case RESOLVE -> new ResolveForm();
            default -> new RemarkForm();
        };
        form.setVersion(page.summary().version());
        model.addAttribute("form", form);
        return page(model, page, action, path);
    }

    String post(String no, Action action, String path, VersionForm form, BindingResult errors,
                List<MultipartFile> files, CurrentUser user, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                String remark = switch (form) {
                    case RemarkForm required -> required.getRemark();
                    case OptionalRemarkForm optional -> optional.getRemark();
                    default -> null;
                };
                Long department = switch (form) {
                    case AssignForm assign -> assign.getDepartmentId();
                    case ReassignForm reassign -> reassign.getDepartmentId();
                    default -> null;
                };
                var result = workflow.perform(no, action, form.getVersion(), remark, department,
                        files == null ? List.of() : files, user);
                success(redirect, action, no, result);
                return action == Action.SEND_BACK ? "redirect:/officer/queue" : "redirect:/complaints/" + no;
            } catch (FormRejectedException rejection) {
                Forms.apply(rejection, errors);
            } catch (StorageException failure) {
                String reference = ErrorReference.next();
                try (var ignored = MDC.putCloseable("ref", reference)) {
                    LOG.error("STORAGE_FAILED ref={} userId={} purpose=RESOLUTION", reference, user.id(), failure);
                }
                errors.reject("validation.complaint.saveFailed", new Object[]{reference}, null);
            }
        }
        if (action == Action.START_REVIEW) {
            workflow.actionPage(no, action, user);
            flash.error(redirect, "validation.version.required");
            return "redirect:/complaints/" + no;
        }
        return page(model, workflow.actionPage(no, action, user), action, path);
    }

    private String page(Model model, ComplaintWorkflowService.ActionPage page, Action action, String path) {
        var complaint = page.summary();
        model.addAttribute("complaint", complaint);
        model.addAttribute("action", action);
        model.addAttribute("postUrl", path.replace("{no}", complaint.complaintNo()));
        model.addAttribute("cancelUrl", "/complaints/" + complaint.complaintNo());
        switch (action) {
            case ASSIGN, REASSIGN -> model.addAttribute("departments", lookups.activeDepartments().stream()
                    .filter(option -> action == Action.ASSIGN || !Objects.equals(option.id(), page.departmentId())).toList());
            case REOPEN -> model.addAttribute("reopensLeft", page.reopensLeft());
            case RESOLVE -> {
                model.addAttribute("maxFiles", FileStorageService.RESOLUTION_LIMIT);
                model.addAttribute("maxFileSizeMb", maxFileSizeMb);
            }
            default -> { }
        }
        return "complaint/action-form";
    }

    private void success(RedirectAttributes redirect, Action action, String no,
                         ComplaintWorkflowService.WorkflowResult result) {
        switch (action) {
            case START_REVIEW -> flash.success(redirect, "flash.complaint.reviewStarted");
            case ASSIGN -> flash.success(redirect, "flash.complaint.assigned", result.department());
            case REASSIGN -> flash.success(redirect, "flash.complaint.reassigned", result.department());
            case START_WORK -> flash.success(redirect, "flash.complaint.workStarted");
            case ADD_REMARK -> flash.success(redirect, "flash.complaint.remarkAdded");
            case RESOLVE -> flash.success(redirect, "flash.complaint.resolved", result.reviewDeadline());
            case CLOSE -> flash.success(redirect, "flash.complaint.closed");
            case REOPEN -> flash.success(redirect, "flash.complaint.reopened");
            case SEND_BACK -> flash.success(redirect, "flash.complaint.sentBack", no);
            case REJECT -> flash.success(redirect, "flash.complaint.rejected");
            default -> throw new IllegalArgumentException("Unsupported workflow action: " + action);
        }
    }
}
