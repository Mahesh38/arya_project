package com.smartgrievance.common.web;

import com.smartgrievance.common.PageResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.springframework.web.util.UriComponentsBuilder;

public record AdminPageLinks(String previous, String next, List<Link> pages,
                             boolean hasPreviousGap, boolean hasNextGap) {
    public record Link(long number, String url, boolean current) {}

    public static AdminPageLinks of(String path, PageResult<?> page, Map<String, ?> filters) {
        var uri = UriComponentsBuilder.fromPath(path).queryParam("size", page.size());
        var values = new LinkedHashMap<String, Object>();
        filters.forEach((key, value) -> {
            if (value != null) {
                uri.queryParam(key, "{" + key + "}");
                values.put(key, value);
            }
        });
        uri.encode();
        long count = Math.max(1, page.totalPages());
        long start = Math.max(1, page.page() - 2L);
        long end = Math.min(count, page.page() + 2L);
        var links = LongStream.rangeClosed(start, end)
                .mapToObj(number -> new Link(number, url(uri, values, number), number == page.page())).toList();
        return new AdminPageLinks(page.hasPrevious() ? url(uri, values, page.page() - 1L) : null,
                page.hasNext() ? url(uri, values, page.page() + 1L) : null, links, start > 1, end < count);
    }

    private static String url(UriComponentsBuilder uri, Map<String, ?> values, long page) {
        return uri.cloneBuilder().queryParam("page", page).buildAndExpand(values).toUriString();
    }
}
