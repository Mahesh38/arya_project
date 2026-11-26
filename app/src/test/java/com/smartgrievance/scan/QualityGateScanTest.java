package com.smartgrievance.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Release gates of M9: the coverage limits stay configured, and the test IDs match the catalogue. */
@Tag("scan")
class QualityGateScanTest {
    private static final Path POM = Path.of("pom.xml");
    private static final Path CATALOGUE = Path.of("..", "docs", "specs", "13a-test-catalogue.md");
    private static final Pattern ID = Pattern.compile("TC-[A-Z]+-[A-Za-z0-9]+");
    private static final Pattern ROW = Pattern.compile("(?m)^\\| (TC-[A-Z]+-[A-Za-z0-9]+) \\|.*\\| (Must|Later) \\|\\s*$");
    /**
     * Route tests whose IDs are built at run time by parameterized tests, so the source text does not contain them:
     * MasterDataRouteWebTest.masterRoutes() makes TC-RT-80…100 and StaffAdminWebTest.routes() makes TC-RT-101…115.
     */
    private static final Map<String, String> GENERATED = Map.of(
            "src/test/java/com/smartgrievance/masterdata/MasterDataRouteWebTest.java", "\"TC-RT-\" + (80 + index * 7 + operation)",
            "src/test/java/com/smartgrievance/staff/StaffAdminWebTest.java", "IntStream.rangeClosed(101, 115)");
    /** Manual checks recorded in the printed test report (doc 14 §11.5) instead of automated tests. */
    private static final Set<String> MANUAL = Set.of("TC-NF-12", "TC-NF-18");

    @Test
    @DisplayName("TC-NF-15 the JaCoCo gates of doc 13 §6.1 are configured and the later gate is switched on")
    void coverageGatesAreConfigured() throws IOException {
        String pom = Files.readString(POM, StandardCharsets.UTF_8);
        assertThat(pom).contains("<coverage.later.skip>false</coverage.later.skip>");
        String core = section(pom, "check-core");
        assertThat(core).contains("com.smartgrievance.workflow.TransitionRules", "com.smartgrievance.access.ComplaintAccessPolicy",
                "<minimum>1.00</minimum>", "<minimum>0.90</minimum>", "<minimum>0.85</minimum>", "<haltOnFailure>true</haltOnFailure>");
        String later = section(pom, "check-later");
        assertThat(later).contains("com.smartgrievance.*.*Service", "<element>BUNDLE</element>", "<minimum>0.80</minimum>",
                "<minimum>0.70</minimum>", "<minimum>0.75</minimum>", "<minimum>0.60</minimum>", "<haltOnFailure>true</haltOnFailure>");
    }

    @Test
    @DisplayName("TC-SCAN-04 every test case ID in the code is in the catalogue and every catalogue case has a test")
    void testIdsMatchTheCatalogue() throws IOException {
        var catalogue = new HashMap<String, String>();
        Matcher rows = ROW.matcher(Files.readString(CATALOGUE, StandardCharsets.UTF_8));
        while (rows.find()) catalogue.put(rows.group(1), rows.group(2));
        assertThat(catalogue).hasSize(446);

        var used = new TreeSet<String>();
        try (Stream<Path> files = Stream.concat(Files.walk(Path.of("src", "test", "java")), Files.walk(Path.of("e2e", "tests")))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".ts")).toList()) {
                ID.matcher(Files.readString(file, StandardCharsets.UTF_8)).results().forEach(match -> used.add(match.group()));
            }
        }
        for (var generator : GENERATED.entrySet()) {
            assertThat(Files.readString(Path.of(generator.getKey()), StandardCharsets.UTF_8)).contains(generator.getValue());
        }
        for (int route = 80; route <= 115; route++) used.add("TC-RT-" + route);
        used.removeIf(id -> id.equals("TC-RT-") || !catalogue.containsKey(id) && id.matches("TC-[A-Z]+-[A-Za-z]*"));

        assertThat(used).as("IDs used in tests but missing from 13a").allMatch(catalogue::containsKey);
        var missing = new TreeSet<String>(catalogue.keySet());
        missing.removeAll(used);
        missing.removeAll(MANUAL);
        assertThat(missing).as("catalogue cases without an automated test").isEmpty();
    }

    private static String section(String pom, String executionId) {
        int start = pom.indexOf("<id>" + executionId + "</id>");
        assertThat(start).as(executionId).isNotNegative();
        return pom.substring(start, pom.indexOf("</execution>", start));
    }
}
