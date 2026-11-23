package com.smartgrievance.common.web;

import com.smartgrievance.common.exception.Subject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectTargetsTest {
    @ParameterizedTest(name = "TC-VE-15 refused {0} always redirects to the documented local path")
    @CsvSource({
            "COMPLAINT,/complaints/GRV-2026-000001", "OTP,/verify-mobile",
            "EXPORT,/admin/complaints?status=ASSIGNED&page=2", "DEPARTMENTS,/admin/departments",
            "CATEGORIES,/admin/categories", "AREAS,/admin/areas", "OFFICERS,/admin/officers", "CITIZENS,/admin/citizens"
    })
    void redirectTargetIsFixed(Subject.Type type, String path) {
        var request = new MockHttpServletRequest("POST", "/action");
        request.addHeader("Referer", "https://example.test/other-page");
        request.setQueryString("status=ASSIGNED&page=2");
        assertThat(RedirectTargets.of(new Subject(type, "GRV-2026-000001"), request)).isEqualTo(path);
    }
}
