package com.smartgrievance.common;

import java.util.List;

import com.smartgrievance.common.exception.BusinessRuleException;
import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.common.exception.StorageException;
import com.smartgrievance.common.exception.Subject;
import com.smartgrievance.common.validation.ValidMobile;
import jakarta.validation.Valid;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.validation.BindingResult;

@TestConfiguration
@Controller
class ErrorTriggerController {
    static final String PRIVATE_MESSAGE = "private-backend-diagnostic";

    @GetMapping("/test-only/errors/{caseName}")
    void error(@PathVariable String caseName) throws Exception {
        switch (caseName) {
            case "not-found" -> throw new NotFoundException("complaint", "GRV-2026-000001", true);
            case "refused" -> throw new BusinessRuleException("BR-47", "error.withdraw.linkedDuplicates",
                    new Subject(Subject.Type.COMPLAINT, "GRV-2026-000001"));
            case "form-rejected" -> throw new FormRejectedException(List.of(new FormError("mobile", "validation.mobile.format")));
            case "storage" -> throw new StorageException(new IllegalStateException(PRIVATE_MESSAGE));
            case "upload-size" -> throw new MaxUploadSizeExceededException(26_000_000);
            case "integrity" -> throw new DataIntegrityViolationException(PRIVATE_MESSAGE);
            case "database" -> throw new DataAccessResourceFailureException(PRIVATE_MESSAGE);
            case "not-acceptable" -> throw new HttpMediaTypeNotAcceptableException(List.of());
            case "media-type" -> throw new HttpMediaTypeNotSupportedException(PRIVATE_MESSAGE);
            default -> throw new IllegalStateException(PRIVATE_MESSAGE);
        }
    }

    @GetMapping("/test-only/typed")
    @ResponseBody
    long typed(@RequestParam long id) { return id; }

    @GetMapping("/test-only/typed/{id}")
    @ResponseBody
    long typedPath(@PathVariable long id) { return id; }

    @GetMapping("/test-only/lookup/{original}")
    @ResponseBody
    String queryAndPath(@PathVariable String original, @RequestParam String email) {
        return original + "|" + email;
    }

    @GetMapping("/notifications/unread-count")
    void unread() { throw new IllegalStateException(PRIVATE_MESSAGE); }

    @PostMapping("/test-only/form")
    @ResponseBody
    String form(@Valid @ModelAttribute SampleForm form, BindingResult result) {
        return result.getFieldErrors().stream().map(error -> error.getField() + "=" + error.getDefaultMessage())
                .reduce((first, next) -> first + "|" + next).orElse("ok");
    }

    public static class SampleForm {
        private Integer slaDays;
        private Long departmentId;
        @ValidMobile private String mobile;

        public Integer getSlaDays() { return slaDays; }
        public void setSlaDays(Integer slaDays) { this.slaDays = slaDays; }
        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
    }
}
