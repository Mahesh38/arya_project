package com.smartgrievance.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("scan")
class FoundationScanTest {
    private static final Path APP = Path.of("src/main");
    private static final Path JAVA = APP.resolve("java");
    private static final Path TEMPLATES = APP.resolve("resources/templates");

    @Test
    @DisplayName("TC-SEC-45 templates use escaped output and external script and style sources")
    void templateContent() throws IOException {
        for (Path template : sources(TEMPLATES, ".html")) {
            var document = Jsoup.parse(Files.readString(template));
            assertThat(document.select("[th:utext], script:not([src],[th:src]), style, [style], form[action]"))
                    .as(template.toString()).isEmpty();
            assertThat(document.select("[href^=javascript:], [src^=javascript:]")).as(template.toString()).isEmpty();
        }
    }

    @Test
    @DisplayName("TC-SCAN-01 templates do not format dates directly")
    void formatting() throws IOException {
        for (Path template : sources(TEMPLATES, ".html")) {
            assertThat(Files.readString(template)).as(template.toString()).doesNotContain("#temporals", "#dates", ".format(");
        }
    }

    @Test
    @DisplayName("TC-SCAN-03 no Java method security annotations")
    void noMethodSecurity() throws IOException {
        for (Path source : sources(JAVA, ".java")) {
            assertThat(Files.readString(source)).as(source.toString())
                    .doesNotContain("@PreAuthorize", "@Secured", "@EnableMethodSecurity");
        }
    }

    @Test
    @DisplayName("TC-Q-42 repository SQL delegates timestamps and transactions to the application")
    void repositorySql() throws IOException {
        var forbidden = Pattern.compile("(?i)\\b(?:TIMESTAMPDIFF|NOW|DATE_ADD)\\s*\\(|\\bINTERVAL\\b|\\bSTART\\s+TRANSACTION\\b");
        for (Path source : sources(JAVA, "Repository.java")) {
            assertThat(Files.readString(source)).as(source.toString()).doesNotContainPattern(forbidden);
        }
    }

    @Test
    @DisplayName("TC-NF-14 controllers do not depend on repositories")
    void layering() throws IOException {
        for (Path source : sources(JAVA, "Controller.java")) {
            assertThat(Files.readString(source)).as(source.toString()).doesNotContainPattern("\\b\\w+Repository\\b");
        }
    }

    @Test
    @DisplayName("TC-NF-05 every status badge colour pair meets WCAG normal-text contrast")
    void contrast() throws IOException {
        String css = Files.readString(APP.resolve("resources/static/css/app.css"));
        var pairs = Pattern.compile(
                "--(status-[a-z-]+|overdue|awaiting)-bg:\\s*#([\\da-fA-F]{6});\\s*--\\1-fg:\\s*#([\\da-fA-F]{6})")
                .matcher(css).results().toList();
        assertThat(pairs).hasSize(17).allSatisfy(pair -> {
            double a = luminance(pair.group(2)) + 0.05;
            double b = luminance(pair.group(3)) + 0.05;
            assertThat(Math.max(a, b) / Math.min(a, b)).as(pair.group(1)).isGreaterThanOrEqualTo(4.5);
        });
    }

    @Test
    @DisplayName("TC-BR-25 complaint events and revisions are insert-only")
    void complaintHistoryIsAppendOnly() throws IOException {
        var write = Pattern.compile("(?i)\\b(?:update|delete\\s+from)\\s+complaint_(?:event|revision)\\b");
        for (Path source : sources(JAVA, ".java")) {
            assertThat(Files.readString(source)).as(source.toString()).doesNotContainPattern(write);
        }
        for (Path source : sources(APP.resolve("resources"), ".sql")) {
            assertThat(Files.readString(source)).as(source.toString()).doesNotContainPattern(write);
        }
    }

    @Test
    @DisplayName("TC-SCAN-02 complaint action controls use the server-provided links")
    void complaintActionsUsePolicyResults() throws IOException {
        var actions = Files.readString(TEMPLATES.resolve("fragments/complaint-actions.html"));
        assertThat(actions).contains("${actionLinks}").doesNotContain("c.status", "sec:authorize", "#authentication");
        var detail = Jsoup.parse(Files.readString(TEMPLATES.resolve("complaint/detail.html")));
        assertThat(detail.select("button, form")).isEmpty();
        assertThat(detail.select("[th:replace]").stream().map(element -> element.attr("th:replace")))
                .anyMatch(fragment -> fragment.contains("complaint-actions :: bar(${actionLinks}"));
    }

    private static double luminance(String rgb) {
        return 0.2126 * channel(rgb.substring(0, 2))
                + 0.7152 * channel(rgb.substring(2, 4)) + 0.0722 * channel(rgb.substring(4, 6));
    }

    private static double channel(String hex) {
        double value = Integer.parseInt(hex, 16) / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    static List<Path> sources(Path directory, String suffix) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(suffix)).toList();
        }
    }
}
