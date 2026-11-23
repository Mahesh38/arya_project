package com.smartgrievance.staff;

import com.smartgrievance.common.PageRequest;
import java.util.LinkedHashMap;
import java.util.Map;

record UserFilter(String q, Long departmentId, Boolean active, Boolean verified, PageRequest request) {
    Map<String, ?> toQueryParams() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("q", q);
        values.put("departmentId", departmentId);
        values.put("active", active);
        values.put("verified", verified);
        return values;
    }
}
