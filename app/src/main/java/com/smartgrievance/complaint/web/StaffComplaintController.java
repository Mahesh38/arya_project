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
import com.smartgrievance.common.web.Flash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.StringWriter;
import java.net.URI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
class StaffComplaintController {
    private static final String QUEUE = "/officer/queue";
    private static final String ALL = "/admin/complaints";
    private static final String EXPORT = "/admin/complaints/export.csv";
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("uuuuMMdd-HHmm");
    private final StaffComplaintService complaints;
    private final MasterDataService lookup;
    private final Flash flash;
    private final Clock clock;

    StaffComplaintController(StaffComplaintService complaints, MasterDataService lookup, Flash flash, Clock clock) {
        this.complaints = complaints;
        this.lookup = lookup;
        this.flash = flash;
        this.clock = clock;
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
        var filter = adminFilter(status, categoryId, departmentId, areaId, from, to, q, overdue,
                AdminFilters.page(page, size), categories, areas, departments);
        list(model, ALL, complaints.allComplaints(filter), filter, categories, areas);
        model.addAttribute("departments", departments);
        model.addAttribute("statusOptions", ComplaintTrackingController.STATUS_OPTIONS);
        model.addAttribute("exportUrl", url(EXPORT, filter));
        return "admin/complaints";
    }

    /** R-32: the filtered list as CSV, at most app.export.max-rows rows (FR-65, D43). */
    @GetMapping(EXPORT)
    ResponseEntity<byte[]> export(@AuthenticationPrincipal CurrentUser admin, @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String categoryId,
                                  @RequestParam(required = false) String departmentId,
                                  @RequestParam(required = false) String areaId, @RequestParam(required = false) String from,
                                  @RequestParam(required = false) String to, @RequestParam(required = false) String q,
                                  @RequestParam(required = false) String overdue, HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        var filter = adminFilter(status, categoryId, departmentId, areaId, from, to, q, overdue, AdminFilters.page(null, null),
                lookup.categories(), lookup.areas(), lookup.departments());
        long matching = complaints.exportCount(filter);
        if (matching > complaints.exportMaxRows()) {
            String target = url(ALL, filter);
            flash.error(request, response, target, "error.export.tooMany", matching, complaints.exportMaxRows());
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
        }
        var csv = new StringWriter();
        complaints.exportCsv(filter, admin.id(), csv);
        String name = "complaints-" + FILE_STAMP.format(LocalDateTime.now(clock)) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private StaffComplaintFilter adminFilter(String status, String categoryId, String departmentId, String areaId,
                                             String from, String to, String q, String overdue,
                                             com.smartgrievance.common.PageRequest paging, List<LookupOption> categories,
                                             List<LookupOption> areas, List<LookupOption> departments) {
        return StaffComplaintFilter.of(status, known(categoryId, categories), known(departmentId, departments),
                "NONE".equals(departmentId), known(areaId, areas), date(from), date(to), q,
                Boolean.TRUE.equals(AdminFilters.flag(overdue)), paging);
    }

    private static String url(String path, StaffComplaintFilter filter) {
        var uri = UriComponentsBuilder.fromPath(path);
        var values = new java.util.LinkedHashMap<String, Object>();
        filter.toQueryParams().forEach((key, value) -> {
            if (value != null) {
                uri.queryParam(key, "{" + key + "}");
                values.put(key, value);
            }
        });
        return uri.encode().buildAndExpand(values).toUriString();
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
