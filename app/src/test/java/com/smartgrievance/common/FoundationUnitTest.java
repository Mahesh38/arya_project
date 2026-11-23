package com.smartgrievance.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.validation.UniqueKeys;
import com.smartgrievance.common.web.DisplayFormat;
import com.smartgrievance.common.web.ErrorReference;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.complaint.StatusGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;

@Tag("unit")
class FoundationUnitTest {
    @Test
    @DisplayName("TC-UT-02 format dates and durations even with a different default locale")
    void displayFormats() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("hi-IN"));
            DisplayFormat formatter = new DisplayFormat();
            assertThat(formatter.dateTime(LocalDate.of(2026, 11, 22).atTime(15, 45))).isEqualTo("22-Nov-2026 03:45 PM");
            assertThat(formatter.date(LocalDate.of(2026, 11, 29))).isEqualTo("29-Nov-2026");
            assertThat(formatter.days(0L)).isEqualTo("0 days");
            assertThat(formatter.days(1L)).isEqualTo("1 day");
            assertThat(formatter.days(3L)).isEqualTo("3 days");
            assertThat(formatter.date(null)).isEmpty();
            assertThat(formatter.dateTime(null)).isEmpty();
            assertThat(formatter.days(null)).isEmpty();
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "SUBMITTED,Submitted,status-submitted,bi-inbox,false",
            "UNDER_REVIEW,Under Review,status-under-review,bi-search,false",
            "ASSIGNED,Assigned,status-assigned,bi-building,false",
            "IN_PROGRESS,In Progress,status-in-progress,bi-tools,false",
            "REOPENED,Reopened,status-reopened,bi-arrow-counterclockwise,false",
            "RESOLVED,Resolved,status-resolved,bi-check2-circle,false",
            "CLOSED,Closed,status-closed,bi-lock,true",
            "REJECTED,Rejected,status-rejected,bi-x-circle,true",
            "DUPLICATE,Duplicate,status-duplicate,bi-files,true",
            "WITHDRAWN,Withdrawn,status-withdrawn,bi-arrow-return-left,true"
    })
    @DisplayName("TC-UT-03 status badge labels, CSS, icons and final states")
    void statusBadges(ComplaintStatus status, String label, String badge, String icon, boolean isFinal) throws Exception {
        String css = new ClassPathResource("static/css/app.css").getContentAsString(StandardCharsets.UTF_8);
        assertThat(status.label()).isEqualTo(label);
        assertThat(status.badgeClass()).isEqualTo(badge);
        assertThat(status.icon()).isEqualTo(icon);
        assertThat(status.isFinal()).isEqualTo(isFinal);
        assertThat(css).contains("." + badge);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,Pending (new + under review),status-group-pending,'SUBMITTED UNDER_REVIEW'",
            "ACTIVE,Active,status-group-active,'ASSIGNED IN_PROGRESS REOPENED RESOLVED'",
            "NOT_FINAL,Open (not finished),status-group-not-final,'SUBMITTED UNDER_REVIEW ASSIGNED IN_PROGRESS REOPENED RESOLVED'",
            "FINAL,Finished,status-group-final,'CLOSED REJECTED DUPLICATE WITHDRAWN'",
            "ALL,All statuses,status-group-all,'SUBMITTED UNDER_REVIEW ASSIGNED IN_PROGRESS REOPENED RESOLVED CLOSED REJECTED DUPLICATE WITHDRAWN'"
    })
    @DisplayName("TC-UT-04 status groups expand to the documented statuses")
    void statusGroups(StatusGroup group, String label, String badge, String statuses) throws Exception {
        String css = new ClassPathResource("static/css/app.css").getContentAsString(StandardCharsets.UTF_8);
        assertThat(group.label()).isEqualTo(label);
        assertThat(group.badgeClass()).isEqualTo(badge);
        assertThat(css).contains("." + badge);
        assertThat(StatusGroup.expand(group.name())).map(ComplaintStatus::name)
                .containsExactlyInAnyOrder(statuses.split(" "));
        assertThat(StatusGroup.expand("CLOSED")).isEqualTo(Set.of(ComplaintStatus.CLOSED));
        assertThat(StatusGroup.expand("FOO")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"uq_app_user_email,email", "uq_app_user_mobile,mobile", "uq_department_name,name",
            "uq_category_name,name", "uq_area_name,name", "uq_complaint_no,"})
    @DisplayName("TC-UT-14 unique keys translate known H2 and MySQL messages")
    void translatesUniqueness(String constraint, String field) {
        for (String diagnostic : List.of(
                "Duplicate entry 'value' for key 'some_table." + constraint + "'",
                "Unique index or primary key violation: \"public." + constraint.toUpperCase(Locale.ROOT) + "_INDEX_1\"")) {
            DuplicateKeyException original = new DuplicateKeyException(diagnostic);
            RuntimeException mapped = UniqueKeys.translate(original);
            if (field == null) {
                assertThat(mapped).isSameAs(original);
            } else {
                assertThat(mapped).isInstanceOfSatisfying(FormRejectedException.class, rejected -> {
                    var error = rejected.errors().getFirst();
                    assertThat(error.field()).isEqualTo(field);
                    assertThat(error.key()).isEqualTo("validation." + field + ".taken");
                });
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"0,0,1,0", "1,1,1,0", "10,1,1,0", "11,2,2,10", "57,6,6,50"})
    @DisplayName("TC-UT-15 page math clamps an out-of-range page")
    void pageMath(long total, long totalPages, int page, long offset) {
        PageRequest request = PageRequest.from(99, null).clampTo(total);
        assertThat(request.page()).isEqualTo(page);
        assertThat(request.offset()).isEqualTo(offset);
        assertThat(new PageResult<>(List.of(), page, request.size(), total).totalPages()).isEqualTo(totalPages);
        var second = new PageResult<>(IntStream.rangeClosed(11, 20).boxed().toList(), 2, 10, 57);
        assertThat(second.firstItemNumber()).isEqualTo(11);
        assertThat(second.lastItemNumber()).isEqualTo(20);
        assertThat(second.hasPrevious()).isTrue();
        assertThat(second.hasNext()).isTrue();
    }

    @Test
    @DisplayName("TC-VE-21 reference format has eight Crockford characters")
    void errorReference() {
        assertThat(IntStream.range(0, 10_000).mapToObj(i -> ErrorReference.next()).toList())
                .allMatch(reference -> reference.matches("ERR-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}"));
    }
}
