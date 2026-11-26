package com.smartgrievance.staff;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.web.AdminFilters;
import com.smartgrievance.common.web.AdminPageLinks;
import com.smartgrievance.common.web.Flash;
import com.smartgrievance.common.web.Forms;
import com.smartgrievance.masterdata.MasterDataService;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.Role;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class StaffAdminController {
    private static final String OFFICERS = "/admin/officers";
    private static final String CITIZENS = "/admin/citizens";
    private static final String STAFF = "/admin/{collection:officers|citizens}";
    private static final String OFFICER_FORM = "admin/officer-form";
    private static final String RESET_FORM = "admin/reset-password";

    private final UserAdminService users;
    private final MasterDataService masterData;
    private final Flash flash;

    StaffAdminController(UserAdminService users, MasterDataService masterData, Flash flash) {
        this.users = users;
        this.masterData = masterData;
        this.flash = flash;
    }

    @GetMapping(STAFF)
    String list(@PathVariable String collection, @RequestParam(required = false) String q,
                @RequestParam(required = false) String departmentId, @RequestParam(required = false) String active,
                @RequestParam(required = false) String verified, @RequestParam(required = false) String page,
                @RequestParam(required = false) String size, Model model) {
        Role role = role(collection);
        UserFilter filter = new UserFilter(q, role == Role.OFFICER ? AdminFilters.id(departmentId) : null,
                AdminFilters.flag(active), role == Role.CITIZEN ? AdminFilters.flag(verified) : null,
                AdminFilters.page(page, size));
        var result = users.list(role, filter);
        model.addAttribute("page", result);
        model.addAttribute("filter", filter);
        model.addAttribute("baseUrl", base(role));
        model.addAttribute("pageLinks", AdminPageLinks.of(base(role), result, filter.toQueryParams()));
        if (role == Role.OFFICER) model.addAttribute("departments", masterData.departments());
        return "admin/" + collection;
    }

    @GetMapping(OFFICERS + "/new")
    String newOfficer(Model model) {
        model.addAttribute("form", new OfficerCreateForm());
        return officerForm(model, null);
    }

    @PostMapping(OFFICERS)
    String createOfficer(@Valid @ModelAttribute("form") OfficerCreateForm form, BindingResult errors, Model model,
                         @AuthenticationPrincipal CurrentUser admin, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                users.createOfficer(form, admin.id());
                flash.success(redirect, "flash.officer.created");
                return "redirect:" + OFFICERS;
            } catch (FormRejectedException rejection) {
                Forms.apply(rejection, errors);
            }
        }
        return officerForm(model, null);
    }

    @GetMapping(OFFICERS + "/{id}/edit")
    String editOfficer(@PathVariable long id, Model model) {
        model.addAttribute("form", users.editForm(id));
        return officerForm(model, id);
    }

    @PostMapping(OFFICERS + "/{id}")
    String updateOfficer(@PathVariable long id, @Valid @ModelAttribute("form") OfficerEditForm form, BindingResult errors,
                         Model model, @AuthenticationPrincipal CurrentUser admin, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                users.updateOfficer(id, form, admin.id());
                flash.success(redirect, "flash.officer.updated");
                return "redirect:" + OFFICERS;
            } catch (FormRejectedException rejection) {
                Forms.apply(rejection, errors);
            }
        }
        return officerForm(model, id);
    }

    @PostMapping(STAFF + "/{id}/{action:activate|deactivate}")
    String activation(@PathVariable String collection, @PathVariable long id, @PathVariable String action,
                      @RequestParam(required = false) Integer version, @AuthenticationPrincipal CurrentUser admin,
                      RedirectAttributes redirect) {
        Role role = role(collection);
        boolean active = "activate".equals(action);
        users.setActive(role, id, version, active, admin.id());
        flash.success(redirect, active ? "flash.account.activated" : "flash.account.deactivated");
        return "redirect:" + base(role);
    }

    @PostMapping(CITIZENS + "/{id}/verify")
    String verify(@PathVariable long id, @RequestParam(required = false) Integer version,
                  @AuthenticationPrincipal CurrentUser admin, RedirectAttributes redirect) {
        users.verify(id, version, admin.id());
        flash.success(redirect, "flash.account.verified");
        return "redirect:" + CITIZENS;
    }

    @GetMapping(STAFF + "/{id}/password")
    String reset(@PathVariable String collection, @PathVariable long id, Model model) {
        model.addAttribute("form", new PasswordResetForm(null, null));
        return resetForm(role(collection), id, model);
    }

    @PostMapping(STAFF + "/{id}/password")
    String reset(@PathVariable String collection, @PathVariable long id, @Valid @ModelAttribute("form") PasswordResetForm form,
                 BindingResult errors, @AuthenticationPrincipal CurrentUser admin, Model model, RedirectAttributes redirect) {
        Role role = role(collection);
        if (!errors.hasErrors()) {
            try {
                users.resetPassword(role, id, form, admin.id());
                flash.success(redirect, "flash.account.passwordReset");
                return "redirect:" + base(role);
            } catch (FormRejectedException rejection) {
                Forms.apply(rejection, errors);
            }
        }
        return resetForm(role, id, model);
    }

    private String resetForm(Role role, long id, Model model) {
        model.addAttribute("user", users.row(role, id));
        model.addAttribute("backUrl", base(role));
        return RESET_FORM;
    }

    private String officerForm(Model model, Long id) {
        model.addAttribute("mode", id == null ? "CREATE" : "EDIT");
        if (id != null) {
            model.addAttribute("officer", users.row(Role.OFFICER, id));
            model.addAttribute("id", id);
        }
        model.addAttribute("departments", masterData.activeDepartments());
        return OFFICER_FORM;
    }

    private static Role role(String collection) { return "officers".equals(collection) ? Role.OFFICER : Role.CITIZEN; }
    private static String base(Role role) { return role == Role.OFFICER ? OFFICERS : CITIZENS; }
}
