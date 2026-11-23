package com.smartgrievance.dashboard;

import static com.smartgrievance.testsupport.Logins.appUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.complaint.StaffComplaintService.SummaryCard;
import com.smartgrievance.complaint.StaffComplaintService.SummaryCards;
import com.smartgrievance.dashboard.DashboardService.CountLine;
import com.smartgrievance.dashboard.DashboardService.DashboardView;
import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/** Dashboard, CSV export and officer summary on the Appendix A demo world (doc 13a TC-UC-50a…c, TC-UC-51a, TC-RT-78). */
class DashboardWebTest extends WebTestBase {
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AppUserDetailsService identities;
    @Autowired JsonMapper json;

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
    }

    @Test
    @DisplayName("TC-UC-50a, TC-RT-78 dashboard cards and tables match the demo world and every link opens a matching list")
    void dashboardCountsAndLinks() throws Exception {
        var admin = appUser(identities, "admin@example.com");
        var result = mvc.perform(get("/admin/dashboard").with(admin)).andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard")).andExpect(model().attributeExists("dash")).andReturn();
        var dash = (DashboardView) result.getModelAndView().getModel().get("dash");
        Map<String, Long> cards = dash.cards().stream()
                .collect(Collectors.toMap(SummaryCard::label, SummaryCard::count, (a, b) -> a, LinkedHashMap::new));
        assertThat(cards).containsExactly(Map.entry("Total", 11L), Map.entry("Pending", 2L), Map.entry("Assigned", 1L),
                Map.entry("In Progress", 1L), Map.entry("Reopened", 1L), Map.entry("Resolved", 2L), Map.entry("Closed", 1L),
                Map.entry("Rejected", 1L), Map.entry("Duplicate", 1L), Map.entry("Withdrawn", 1L),
                Map.entry("Overdue", dash.overdueTotal()));
        assertThat(dash.overdue()).extracting(DashboardService.OverdueItem::complaintNo).contains(number(5));
        assertThat(dash.overdue()).hasSize((int) Math.min(dash.overdueTotal(), DashboardService.OVERDUE_LIMIT));
        assertThat(dash.overdue()).allSatisfy(item -> assertThat(item.daysOverdue()).isPositive());
        assertThat(dash.byDepartment()).extracting(CountLine::name).last().isEqualTo("(Not assigned)");
        assertThat(dash.byArea()).hasSize(7);
        assertThat(sum(dash.byCategory())).isEqualTo(11);
        assertThat(sum(dash.byDepartment())).isEqualTo(11);
        assertThat(sum(dash.byArea())).isEqualTo(11);

        var links = new ArrayList<Map.Entry<String, Long>>();
        dash.cards().forEach(card -> links.add(Map.entry(card.url(), card.count())));
        for (var line : concat(dash.byCategory(), dash.byDepartment(), dash.byArea())) {
            links.add(Map.entry(line.openUrl(), line.open()));
            links.add(Map.entry(line.closedUrl(), line.closed()));
        }
        for (var link : links) {
            var list = mvc.perform(get(link.getKey()).with(admin)).andExpect(status().isOk()).andReturn();
            var page = (PageResult<?>) list.getModelAndView().getModel().get("page");
            assertThat(page.totalItems()).as(link.getKey()).isEqualTo(link.getValue());
        }
        var html = Html.of(result);
        assertThat(html.links()).contains("/admin/complaints?overdue=true", "/admin/complaints?status=PENDING");
        assertThat(html.text("main")).contains("Overdue (top 10)", "By department", "By category", "By area");
    }

    @Test
    @DisplayName("TC-UC-50c the data-chart attribute holds the by-department numbers")
    void chartDataMatchesTable() throws Exception {
        var admin = appUser(identities, "admin@example.com");
        var result = mvc.perform(get("/admin/dashboard").with(admin)).andExpect(status().isOk()).andReturn();
        var dash = (DashboardView) result.getModelAndView().getModel().get("dash");
        var attribute = Html.of(result).document().getElementById("chart-data").attr("data-chart");
        var chart = json.readTree(attribute);
        assertThat(json.convertValue(chart.get("labels"), List.class))
                .isEqualTo(dash.byDepartment().stream().map(CountLine::name).toList());
        assertThat(json.convertValue(chart.get("open"), List.class)).map(value -> ((Number) value).longValue())
                .isEqualTo(dash.byDepartment().stream().map(CountLine::open).toList());
        assertThat(json.convertValue(chart.get("closed"), List.class)).map(value -> ((Number) value).longValue())
                .isEqualTo(dash.byDepartment().stream().map(CountLine::closed).toList());
        assertThat(Html.of(result).document().select("script:not([src])")).isEmpty();
    }

    @Test
    @DisplayName("TC-UC-50b CSV export of open complaints has a BOM, a header and the seven open rows")
    void exportsOpenComplaints() throws Exception {
        var admin = appUser(identities, "admin@example.com");
        var response = mvc.perform(get("/admin/complaints/export.csv").with(admin).param("status", "NOT_FINAL"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andReturn().getResponse();
        String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).startsWith("﻿\"Complaint No\",");
        var lines = body.substring(1).split("\r\n");
        assertThat(lines).hasSize(8);
        assertThat(List.of(lines).subList(1, 8)).map(line -> line.substring(1, 16)).containsExactlyInAnyOrder(
                number(1), number(3), number(4), number(5), number(7), number(8), number(11));
    }

    @Test
    @DisplayName("TC-UC-51a officer summary cards count the officer's department only")
    void officerSummary() throws Exception {
        RequestPostProcessor anita = appUser(identities, "anita.deshmukh@example.com");
        var result = mvc.perform(get("/officer/queue").with(anita)).andExpect(status().isOk()).andReturn();
        var summary = (SummaryCards) result.getModelAndView().getModel().get("summary");
        Map<String, Long> cards = summary.cards().stream().collect(Collectors.toMap(SummaryCard::label, SummaryCard::count));
        assertThat(cards).containsEntry("In Progress", 1L).containsEntry("Duplicate", 1L).containsEntry("Assigned", 0L)
                .doesNotContainKey("Pending");
        assertThat(cards.values().stream().mapToLong(Long::longValue).sum() - cards.get("Overdue")).isEqualTo(2);
        assertThat(summary.cards()).allSatisfy(card -> assertThat(card.url()).startsWith("/officer/queue?status="));
    }

    private String number(int n) {
        return "GRV-%d-%06d".formatted(java.time.LocalDate.now(testClock).getYear(), n);
    }

    private static long sum(List<CountLine> lines) {
        return lines.stream().mapToLong(line -> line.open() + line.closed()).sum();
    }

    @SafeVarargs
    private static List<CountLine> concat(List<CountLine>... lists) {
        var all = new ArrayList<CountLine>();
        for (var list : lists) all.addAll(list);
        return all;
    }
}
