package com.smartgrievance.scan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import com.smartgrievance.testsupport.WebTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("scan")
class MasterDataDeletionScanTest extends WebTestBase {
    private static final Pattern HARD_DELETE =
            Pattern.compile("(?i)\\bdelete\\s+from\\s+(?:department|category|area|app_user)\\b");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlers;

    @Test
    @DisplayName("TC-BR-26 admin routes and persistence have no hard-delete operation for master data or accounts")
    void administratorChangesActiveStateInsteadOfDeletingRows() throws Exception {
        var admin = handlers.getHandlerMethods().keySet().stream()
                .filter(mapping -> mapping.getPatternValues().stream().anyMatch(path -> path.startsWith("/admin/")))
                .toList();
        assertThat(admin).isNotEmpty().allSatisfy(mapping -> {
            assertThat(mapping.getMethodsCondition().getMethods()).doesNotContain(RequestMethod.DELETE);
            assertThat(mapping.getPatternValues()).allSatisfy(path -> assertThat(path).doesNotContain("/delete"));
        });
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            for (var source : sources.filter(path -> path.toString().endsWith("Repository.java")).toList()) {
                assertThat(Files.readString(source)).as(source.toString()).doesNotContainPattern(HARD_DELETE);
            }
        }
    }
}
