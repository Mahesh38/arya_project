package com.smartgrievance.scan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("scan")
class AuthFormTemplateScanTest {
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Pattern FIELD = Pattern.compile("'([A-Za-z][A-Za-z0-9]*)'");
    private static final Pattern FORM_FRAGMENT = Pattern.compile(":: (?:accountField|password|text|textarea|select)\\(");

    @ParameterizedTest
    @ValueSource(strings = {"register", "change-password", "verify-mobile"})
    @DisplayName("TC-VE-28 each account error summary follows the visible field order")
    void errorSummaryFollowsInputs(String screen) throws Exception {
        var page = Jsoup.parse(Files.readString(TEMPLATES.resolve("auth/" + screen + ".html")));
        var form = page.selectFirst("form[th:object]");
        assertThat(form).as(screen).isNotNull();
        var summary = form.selectFirst("[th:replace*=:: errorSummary]");
        assertThat(summary).as(screen).isNotNull();
        var namesInSummary = FIELD.matcher(summary.attr("th:replace")).results()
                .map(match -> match.group(1)).toList();
        var namesInForm = new ArrayList<String>();
        for (var element : form.select("[th:field], [th:replace]")) {
            if (element.hasAttr("th:field") && !"hidden".equals(element.attr("type"))) {
                assertThat(element.hasAttr("id")).isFalse();
                namesInForm.add(element.attr("th:field").replace("*{", "").replace("}", ""));
            } else if (FORM_FRAGMENT.matcher(element.attr("th:replace")).find()) {
                var field = FIELD.matcher(element.attr("th:replace"));
                assertThat(field.find()).isTrue();
                namesInForm.add(field.group(1));
            }
        }
        assertThat(namesInSummary).isNotEmpty().containsExactlyElementsOf(namesInForm);
        var fragments = Jsoup.parse(Files.readString(TEMPLATES.resolve("fragments/form.html")));
        assertThat(fragments.select("[th:field][id]")).isEmpty();
    }
}
