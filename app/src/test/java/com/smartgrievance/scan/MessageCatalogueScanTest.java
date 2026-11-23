package com.smartgrievance.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("scan")
class MessageCatalogueScanTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path JAVA = Path.of("src/main/java");
    private static final String KEY = "(?:(?:validation|error|flash|login|notice|confirm|notify|page|form)"
            + "\\.[a-z][A-Za-z0-9]*(?:\\.[A-Za-z0-9]+)*|typeMismatch(?:\\.[A-Za-z0-9]+)*)";
    private static final Pattern LITERAL = Pattern.compile("(?:#?\\{|\")(" + KEY + ")(?:}|\")");
    private static final int COMPLAINT_NUMBER = 15;
    private static final int DEPARTMENT = 100;
    private static final int REMARK = 121;
    private static final int TITLE = 101;
    private static final int DATE = 11;
    private static final int DAYS = 2;
    private static final Map<String, int[]> NOTIFICATION_ARGUMENTS = Map.ofEntries(
            Map.entry("notify.submitted.owner", new int[]{COMPLAINT_NUMBER}),
            Map.entry("notify.submitted.admin", new int[]{COMPLAINT_NUMBER, TITLE}),
            Map.entry("notify.reviewStarted", new int[]{COMPLAINT_NUMBER}),
            Map.entry("notify.assigned.citizen", new int[]{COMPLAINT_NUMBER, DEPARTMENT}),
            Map.entry("notify.assigned.officer", new int[]{COMPLAINT_NUMBER}),
            Map.entry("notify.reassigned.citizen", new int[]{COMPLAINT_NUMBER, DEPARTMENT, REMARK}),
            Map.entry("notify.reassigned.officer", new int[]{COMPLAINT_NUMBER, REMARK}),
            Map.entry("notify.workStarted", new int[]{COMPLAINT_NUMBER, REMARK}),
            Map.entry("notify.remark", new int[]{COMPLAINT_NUMBER, REMARK}),
            Map.entry("notify.resolved.owner", new int[]{COMPLAINT_NUMBER, REMARK, DATE}),
            Map.entry("notify.resolved.watcher", new int[]{COMPLAINT_NUMBER, REMARK}),
            Map.entry("notify.rejected", new int[]{COMPLAINT_NUMBER, REMARK}),
            Map.entry("notify.duplicate.owner", new int[]{COMPLAINT_NUMBER, COMPLAINT_NUMBER}),
            Map.entry("notify.duplicate.watcher", new int[]{COMPLAINT_NUMBER, COMPLAINT_NUMBER}),
            Map.entry("notify.reopened", new int[]{COMPLAINT_NUMBER, REMARK}),
            Map.entry("notify.closed", new int[]{COMPLAINT_NUMBER}),
            Map.entry("notify.autoClosed", new int[]{COMPLAINT_NUMBER, DAYS}),
            Map.entry("notify.sentBack", new int[]{COMPLAINT_NUMBER, DEPARTMENT, REMARK}),
            Map.entry("notify.edited", new int[]{COMPLAINT_NUMBER}),
            Map.entry("notify.withdrawn", new int[]{COMPLAINT_NUMBER, REMARK}));

    @Test
    @DisplayName("TC-VE-27 message catalogue is unique, readable and covers literal message keys")
    void catalogue() throws Exception {
        Path file = RESOURCES.resolve("messages.properties");
        Properties entries = new Properties();
        try (var input = Files.newBufferedReader(file)) { entries.load(input); }
        var keys = new HashSet<String>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String key = line.substring(0, line.indexOf('=')).strip();
            assertThat(keys.add(key)).as("duplicate " + key).isTrue();
            assertThat(key).matches(KEY);
            String value = entries.getProperty(key);
            assertThat(value).as(key).isNotBlank().doesNotContain("'", "!")
                    .doesNotContainPattern("<[a-zA-Z/][^>]*>");
            if (value.matches(".*\\{[0-9]+[},].*")) {
                assertThat(value).doesNotContain("{min}", "{max}");
                Object[] args = {2, 2, 2};
                if (key.startsWith("notify.")) {
                    assertThat(NOTIFICATION_ARGUMENTS).containsKey(key);
                    args = Arrays.stream(NOTIFICATION_ARGUMENTS.get(key)).mapToObj(max -> "X".repeat(max)).toArray();
                }
                String rendered = new MessageFormat(value, Locale.ENGLISH).format(args);
                if (key.startsWith("notify.")) {
                    assertThat(rendered).as(key).hasSizeLessThanOrEqualTo(300)
                            .doesNotContainPattern("\\{[0-9]+}");
                }
            } else {
                assertThat(value.replace("{min}", "").replace("{max}", "")).doesNotContain("{", "}");
            }
        }
        assertThat(keys).hasSize(182);
        for (Path source : FoundationScanTest.sources(RESOURCES.resolve("templates"), ".html")) {
            assertThat(LITERAL.matcher(Files.readString(source)).results().map(match -> match.group(1)))
                    .allSatisfy(key -> assertThat(keys).as(source.toString()).contains(key));
        }
        for (Path source : FoundationScanTest.sources(JAVA, ".java")) {
            assertThat(LITERAL.matcher(Files.readString(source)).results().map(match -> match.group(1)))
                    .allSatisfy(key -> assertThat(keys).as(source.toString()).contains(key));
        }
    }

    @Test
    @DisplayName("TC-VE-26 every submitted form annotation declares a catalogue message")
    void constraintMessages() throws Exception {
        Properties messages = new Properties();
        try (var input = Files.newBufferedReader(RESOURCES.resolve("messages.properties"))) {
            messages.load(input);
        }
        Pattern constraint = Pattern.compile("@(?:NotNull|NotBlank|NotEmpty|Size|Min|Max|Email|Pattern|"
                + "ValidMobile|StrongPassword|FieldsMatch|FieldsDiffer)\\b(?:\\(([^)]*)\\))?");
        Pattern message = Pattern.compile("message\\s*=\\s*\"\\{(" + KEY + ")}\"");
        for (Path form : FoundationScanTest.sources(JAVA, "Form.java")) {
            for (var annotation : constraint.matcher(Files.readString(form)).results().toList()) {
                assertThat(annotation.group(1)).as(form.toString()).isNotNull();
                var matcher = message.matcher(annotation.group(1));
                assertThat(matcher.find()).as(form + " " + annotation.group()).isTrue();
                assertThat(messages).containsKey(matcher.group(1));
            }
        }
    }
}
