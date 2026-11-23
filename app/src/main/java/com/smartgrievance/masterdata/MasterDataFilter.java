package com.smartgrievance.masterdata;

import com.smartgrievance.common.PageRequest;
import java.util.Map;

record MasterDataFilter(Boolean active, PageRequest request) {
    int page() { return request.page(); }
    int size() { return request.size(); }
    Map<String, ?> toQueryParams() { return active == null ? Map.of() : Map.of("active", active); }
}
