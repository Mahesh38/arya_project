package com.smartgrievance.complaint.web;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.common.web.AdminFilters;
import com.smartgrievance.common.web.AdminPageLinks;
import com.smartgrievance.complaint.StaffComplaintFilter;
import com.smartgrievance.complaint.StaffComplaintService;
import com.smartgrievance.complaint.StatusGroup;
import com.smartgrievance.masterdata.LookupOption;
import com.smartgrievance.masterdata.MasterDataService;
import com.smartgrievance.user.CurrentUser;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class StaffComplaintController {
    private static final String QUEUE = "/officer/queue";
    private static final String ALL = "/admin/complaints";
    private final StaffComplaintService complaints;
    private final MasterDataService lookup;

    StaffComplaintController(StaffComplaintService complaints, MasterDataService lookup) {
        this.complaints = complaints;
        this.lookup = lookup;
    }

    @GetMapping(QUEUE)
    String queue(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String status,
                 @RequestParam(required = false) String categoryId, @RequestParam(required = false) String areaId,
                 @RequestParam(required = false) String q, @RequestParam(required = false) String overdue,
                 @RequestParam(required = false) String page, @RequestParam(required = false) String size, Model model) {
        String selected = status == null ? "ACTIVE" : status;
        if (StatusGroup.expand(selected).size() > 1 && !List.of("ACTIVE", "ALL").contains(selected)) selected = "ALL";
        var categories = lookup.categories();
        var areas = lookup.areas();
        var filter = StaffComplaintFilter.of(selected, known(categoryId, categories), null, false, known(areaId, areas),
                null, null, q, Boolean.TRUE.equals(AdminFilters.flag(overdue)), AdminFilters.page(page, size));
        list(model, QUEUE, complaints.departmentQueue(user, filter), filter, categories, areas);
        model.addAttribute("summary", complaints.officerSummary(user));
        model.addAttribute("departmentName", lookup.departments().stream()
                .filter(department -> Long.valueOf(department.id()).equals(user.departmentId())).map(LookupOption::name)
                .findFirst().orElse(null));
        model.addAttribute("statusOptions", ComplaintTrackingController.STATUS_OPTIONS.stream()
                .filter(option -> !List.of("PENDING", "NOT_FINAL", "FINAL", "SUBMITTED", "UNDER_REVIEW").contains(option.value())).toList());
        return "officer/queue";
    }

    @GetMapping(ALL)
    String index(@RequestParam(required = false) String status, @RequestParam(required = false) String categoryId,
                 @RequestParam(required = false) String departmentId, @RequestParam(required = false) String areaId,
                 @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                 @RequestParam(required = false) String q, @RequestParam(required = false) String overdue,
                 @RequestParam(required = false) String page, @RequestParam(required = false) String size, Model model) {
        var categories = lookup.categories();
        var areas = lookup.areas();
        var departments = lookup.departments();
        var filter = StaffComplaintFilter.of(status, known(categoryId, categories), known(departmentId, departments),
                "NONE".equals(departmentId), known(areaId, areas), date(from), date(to), q,
                Boolean.TRUE.equals(AdminFilters.flag(overdue)), AdminFilters.page(page, size));
        list(model, ALL, complaints.allComplaints(filter), filter, categories, areas);
        model.addAttribute("departments", departments);
        model.addAttribute("statusOptions", ComplaintTrackingController.STATUS_OPTIONS);
        return "admin/complaints";
    }

    private static void list(Model model, String path, PageResult<?> page, StaffComplaintFilter filter,
                             List<LookupOption> categories, List<LookupOption> areas) {
        model.addAttribute("page", page);
        model.addAttribute("pageLinks", AdminPageLinks.of(path, page, filter.toQueryParams()));
        model.addAttribute("filter", filter);
        model.addAttribute("categories", categories);
        model.addAttribute("areas", areas);
    }

    private static Long known(String input, List<LookupOption> options) {
        Long id = AdminFilters.id(input);
        return id != null && options.stream().anyMatch(option -> option.id() == id) ? id : null;
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        try {
            var parsed = LocalDate.parse(value);
            return parsed.getYear() >= 1000 && parsed.getYear() <= 9998 ? parsed : null;
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }
}
