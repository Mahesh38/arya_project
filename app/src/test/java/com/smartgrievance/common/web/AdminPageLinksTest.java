package com.smartgrievance.common.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.smartgrievance.common.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPageLinksTest {
    @Test
    @DisplayName("Admin pagination preserves literal search characters and the current page")
    void preservesSearchAndActiveFilters() {
        var page = new PageResult<>(List.of("Asha"), 4, 1, 10);
        String search = "asha+ops@example.test";
        var links = AdminPageLinks.of("/admin/officers", page, Map.of("q", search, "active", true));
        var next = UriComponentsBuilder.fromUriString(links.next()).build(true).getQueryParams();

        assertThat(URLDecoder.decode(next.getFirst("q"), StandardCharsets.UTF_8)).isEqualTo(search);
        assertThat(next).containsEntry("active", List.of("true"))
                .containsEntry("page", List.of("5")).containsEntry("size", List.of("1"));
        assertThat(links.pages()).extracting(AdminPageLinks.Link::number).containsExactly(2L, 3L, 4L, 5L, 6L);
        assertThat(links.pages().stream().filter(AdminPageLinks.Link::current))
                .singleElement().extracting(AdminPageLinks.Link::number).isEqualTo(4L);
    }
}
