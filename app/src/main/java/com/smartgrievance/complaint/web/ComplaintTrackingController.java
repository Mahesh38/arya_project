package com.smartgrievance.complaint.web;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.common.web.AdminFilters;
import com.smartgrievance.common.web.AdminPageLinks;
import com.smartgrievance.complaint.CitizenListFilter;
import com.smartgrievance.complaint.ComplaintQueryService;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.complaint.StatusGroup;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ComplaintTrackingController {
    public record StatusOption(String value, String label) {}

    private static final Set<Action> DEFERRED_ACTIONS = Set.of(Action.MARK_DUPLICATE, Action.WATCH, Action.UNWATCH);

    static final List<StatusOption> STATUS_OPTIONS = Stream.concat(
            Arrays.stream(StatusGroup.values()).map(group -> new StatusOption(group.name(), group.label())),
            Arrays.stream(ComplaintStatus.values()).map(status -> new StatusOption(status.name(), status.label()))).toList();
    private final ComplaintQueryService complaints;

    ComplaintTrackingController(ComplaintQueryService complaints) {
        this.complaints = complaints;
    }

    @GetMapping("/citizen/complaints")
    String myComplaints(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String status,
            @RequestParam(required = false) String q, @RequestParam(required = false) String page,
            @RequestParam(required = false) String size, Model model) {
        var filter = filter(status, q, page, size);
        addList(model, "/citizen/complaints", complaints.myComplaints(user, filter), filter);
        model.addAttribute("verifyBanner", !user.mobileVerified());
        return "citizen/my-complaints";
    }

    @GetMapping("/citizen/watching")
    String watching(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String status,
            @RequestParam(required = false) String page, @RequestParam(required = false) String size, Model model) {
        var filter = filter(status, null, page, size);
        addList(model, "/citizen/watching", complaints.watching(user, filter), filter);
        return "citizen/watching";
    }

    @GetMapping("/complaints/{no}")
    String detail(@PathVariable String no, @AuthenticationPrincipal CurrentUser user, Model model) {
        var complaint = complaints.detail(no, user);
        model.addAttribute("c", complaint);
        model.addAttribute("actionLinks", ActionLinks.forComplaint(complaint.complaintNo(), user.role(), complaint.actions())
                .stream().filter(link -> !DEFERRED_ACTIONS.contains(link.action())).toList());
        return "complaint/detail";
    }

    private static void addList(Model model, String path, PageResult<?> page, CitizenListFilter filter) {
        model.addAttribute("page", page);
        model.addAttribute("pageLinks", AdminPageLinks.of(path, page, filter.toQueryParams()));
        model.addAttribute("filter", new CitizenListFilter(filter.status(), filter.q(), page.page(), page.size()));
        model.addAttribute("statusOptions", STATUS_OPTIONS);
    }

    private static CitizenListFilter filter(String status, String q, String page, String size) {
        return CitizenListFilter.of(status, q, AdminFilters.page(page, size));
    }
}
