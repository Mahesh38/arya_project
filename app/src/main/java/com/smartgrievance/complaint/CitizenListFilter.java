package com.smartgrievance.complaint;

import com.smartgrievance.common.PageRequest;
import java.util.Map;

public record CitizenListFilter(String status, String q, int page, int size) {
    public static CitizenListFilter of(String status, String q, PageRequest paging) {
        return new CitizenListFilter(StatusGroup.expand(status).isEmpty() ? "ALL" : status, q, paging.page(), paging.size());
    }

    public Map<String, String> toQueryParams() {
        return q == null ? Map.of("status", status) : Map.of("status", status, "q", q);
    }

    PageRequest paging() {
        return new PageRequest(page, size);
    }
}
