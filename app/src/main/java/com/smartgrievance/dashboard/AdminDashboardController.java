package com.smartgrievance.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** R-78 (doc 09 §3.8). The officer summary cards (FR-63, UC-51) are part of the department queue (R-30). */
@Controller
class AdminDashboardController {
    private final DashboardService dashboard;

    AdminDashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/admin/dashboard")
    String dashboard(Model model) {
        model.addAttribute("dash", dashboard.adminDashboard());
        return "admin/dashboard";
    }
}
