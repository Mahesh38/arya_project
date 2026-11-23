package com.smartgrievance.masterdata;

import com.smartgrievance.common.web.AdminPageLinks;
import com.smartgrievance.common.web.AdminFilters;
import com.smartgrievance.common.web.Flash;
import com.smartgrievance.common.web.Forms;
import com.smartgrievance.user.CurrentUser;
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
class MasterDataAdminController {
    private static final String MASTER = "/admin/{collection:departments|categories|areas}";
    private static final String DESCRIBED = "/admin/{collection:departments|areas}";
    private static final String CATEGORIES = "/admin/categories";
    private final MasterDataService data;
    private final Flash flash;

    MasterDataAdminController(MasterDataService data, Flash flash) {
        this.data = data;
        this.flash = flash;
    }

    @GetMapping(MASTER)
    String list(@PathVariable String collection, @RequestParam(required = false) String active,
                @RequestParam(required = false) String page, @RequestParam(required = false) String size, Model model) {
        var type = MasterType.forPath(collection);
        var filter = new MasterDataFilter(AdminFilters.flag(active), AdminFilters.page(page, size));
        var result = data.list(type, filter);
        model.addAttribute("page", result);
        model.addAttribute("filter", filter);
        model.addAttribute("baseUrl", type.url());
        model.addAttribute("pageLinks", AdminPageLinks.of(type.url(), result, filter.toQueryParams()));
        return "admin/" + type.plural;
    }

    @GetMapping({MASTER + "/new", MASTER + "/{id}/edit"})
    String form(@PathVariable String collection, @PathVariable(required = false) Long id, Model model) {
        var type = MasterType.forPath(collection);
        model.addAttribute("form", data.form(type, id));
        return showForm(type, id, model);
    }

    @PostMapping({DESCRIBED, DESCRIBED + "/{id}"})
    String saveDescribed(@PathVariable String collection, @PathVariable(required = false) Long id,
                         @Valid @ModelAttribute("form") DescribedForm form, BindingResult errors,
                         @AuthenticationPrincipal CurrentUser admin, Model model, RedirectAttributes redirect) {
        return save(MasterType.forPath(collection), id, form, errors, admin, model, redirect);
    }

    @PostMapping({CATEGORIES, CATEGORIES + "/{id}"})
    String saveCategory(@PathVariable(required = false) Long id, @Valid @ModelAttribute("form") CategoryForm form,
                        BindingResult errors, @AuthenticationPrincipal CurrentUser admin, Model model, RedirectAttributes redirect) {
        return save(MasterType.CATEGORY, id, form, errors, admin, model, redirect);
    }

    @PostMapping(MASTER + "/{id}/{action:activate|deactivate}")
    String activation(@PathVariable String collection, @PathVariable long id, @PathVariable String action,
                      @AuthenticationPrincipal CurrentUser admin, RedirectAttributes redirect) {
        var type = MasterType.forPath(collection);
        boolean active = "activate".equals(action);
        data.setActive(type, id, active, admin.id());
        flash.success(redirect, type.message(active ? "activated" : "deactivated"));
        return "redirect:" + type.url();
    }

    private String save(MasterType type, Long id, NameForm form, BindingResult errors,
                        CurrentUser admin, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                data.save(type, id, form, admin.id());
                flash.success(redirect, type.message("saved"));
                return "redirect:" + type.url();
            } catch (com.smartgrievance.common.exception.FormRejectedException rejection) {
                Forms.apply(rejection, errors);
            }
        }
        if (id != null) data.form(type, id);
        return showForm(type, id, model);
    }

    private String showForm(MasterType type, Long id, Model model) {
        model.addAttribute("mode", id == null ? "CREATE" : "EDIT");
        model.addAttribute("id", id);
        if (type == MasterType.CATEGORY) model.addAttribute("departments", data.activeDepartments());
        return "admin/" + type.table + "-form";
    }
}
