package com.smartgrievance.complaint;

import com.smartgrievance.common.PageRequest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public record StaffComplaintFilter(String status, Long categoryId, Long departmentId, boolean unassigned, Long areaId,
                                   LocalDate from, LocalDate to, String q, boolean overdue, PageRequest paging) {
    public static StaffComplaintFilter of(String status, Long categoryId, Long departmentId, boolean unassigned,
                                          Long areaId, LocalDate from, LocalDate to, String q, boolean overdue,
                                          PageRequest paging) {
        String normalized = StatusGroup.expand(status).isEmpty() ? "ALL" : status;
        return new StaffComplaintFilter(normalized, categoryId, departmentId, unassigned, areaId, from, to, q, overdue, paging);
    }

    public boolean invalidDateRange() { return from != null && to != null && from.isAfter(to); }

    public Map<String, Object> toQueryParams() {
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("status", status);
        parameters.put("categoryId", categoryId);
        parameters.put("departmentId", unassigned ? "NONE" : departmentId);
        parameters.put("areaId", areaId);
        parameters.put("from", from);
        parameters.put("to", to);
        parameters.put("q", q);
        parameters.put("overdue", overdue ? "true" : null);
        return parameters;
    }
}
