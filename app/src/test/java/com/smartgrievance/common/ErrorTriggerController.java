package com.smartgrievance.common;

import java.util.List;

import com.smartgrievance.common.exception.BusinessRuleException;
import com.smartgrievance.common.exception.ConcurrentUpdateException;
import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.exception.InvalidTransitionException;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.common.exception.StorageException;
import com.smartgrievance.common.exception.Subject;
import com.smartgrievance.common.validation.ValidMobile;
import com.smartgrievance.common.validation.UniqueKeys;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Controller;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.BindingResult;

@TestConfiguration
@Controller
class ErrorTriggerController {
    static final String PRIVATE_MESSAGE = "private-backend-diagnostic";
    private static final Subject COMPLAINT = new Subject(Subject.Type.COMPLAINT, "GRV-2026-000001");
    private final MessageSource messages;

    ErrorTriggerController(MessageSource messages) {
        this.messages = messages;
    }

    @GetMapping("/test-only/errors/{caseName}")
    void error(@PathVariable String caseName, HttpServletResponse response) throws Exception {
        switch (caseName) {
            case "not-found" -> throw new NotFoundException("complaint", "GRV-2026-000001", true);
            case "refused" -> throw new BusinessRuleException("BR-47", "error.withdraw.linkedDuplicates", COMPLAINT);
            case "transition" -> throw new InvalidTransitionException("error.state.notAllowed", COMPLAINT);
            case "concurrent" -> throw new ConcurrentUpdateException("error.concurrent.complaint", COMPLAINT);
            case "form-rejected" -> throw new FormRejectedException(List.of(new FormError("mobile", "validation.mobile.format")));
            case "storage" -> throw new StorageException(new IllegalStateException(PRIVATE_MESSAGE));
            case "upload-size" -> throw new MaxUploadSizeExceededException(26_000_000);
            case "integrity" -> throw new DataIntegrityViolationException(PRIVATE_MESSAGE);
            case "unknown-unique" -> throw new DuplicateKeyException("Duplicate entry for key 'uq_complaint_no'");
            case "database" -> throw new DataAccessResourceFailureException(PRIVATE_MESSAGE);
            case "not-acceptable" -> throw new HttpMediaTypeNotAcceptableException(List.of());
            case "media-type" -> throw new HttpMediaTypeNotSupportedException(PRIVATE_MESSAGE);
            case "access" -> throw new AccessDeniedException(PRIVATE_MESSAGE);
            case "authentication" -> throw new BadCredentialsException(PRIVATE_MESSAGE);
            case "async-client-gone" -> {
                response.flushBuffer();
                throw new AsyncRequestNotUsableException(PRIVATE_MESSAGE);
            }
            case "client-abort" -> {
                response.flushBuffer();
                throw new ClientAbortException(PRIVATE_MESSAGE);
            }
            default -> throw new IllegalStateException(PRIVATE_MESSAGE);
        }
    }

    @GetMapping("/test-only/typed")
    @ResponseBody
    long typed(@RequestParam long id) { return id; }

    @GetMapping("/test-only/typed/{id}")
    @ResponseBody
    long typedPath(@PathVariable long id) { return id; }

    @GetMapping("/test-only/positive")
    @ResponseBody
    int positive(@RequestParam @Min(1) int value) { return value; }

    @PostMapping("/test-only/json")
    @ResponseBody
    int json(@RequestBody SmallJson body) { return body.number(); }

    @PostMapping("/test-only/multipart")
    @ResponseBody
    String multipart(@RequestPart MultipartFile file) { return file.getOriginalFilename(); }

    @GetMapping("/test-only/lookup/{original}")
    @ResponseBody
    String queryAndPath(@PathVariable String original, @RequestParam String email) {
        return original + "|" + email;
    }

    @GetMapping(path = "/notifications/unread-count", headers = "X-Test-Error")
    void unread() { throw new IllegalStateException(PRIVATE_MESSAGE); }

    @PostMapping("/test-only/form")
    @ResponseBody
    String form(@Valid @ModelAttribute SampleForm form, BindingResult result) {
        return formErrors(result);
    }

    @PostMapping("/test-only/form-unhandled")
    @ResponseBody
    String formWithoutBindingResult(@Valid @ModelAttribute SampleForm form) {
        return "ok";
    }

    @PostMapping("/test-only/form-unique")
    @ResponseBody
    String formUnique(@ModelAttribute SampleForm form, BindingResult result) {
        try {
            throw UniqueKeys.translate(new DuplicateKeyException(
                    "Duplicate entry '" + PRIVATE_MESSAGE + "' for key 'uq_app_user_mobile'"));
        } catch (FormRejectedException failure) {
            for (FormError error : failure.errors()) result.rejectValue(error.field(), error.key(), error.args(), null);
        }
        return formErrors(result);
    }

    private String formErrors(BindingResult result) {
        return result.getFieldErrors().stream()
                .map(error -> error.getField() + "=" + messages.getMessage(error, LocaleContextHolder.getLocale()))
                .reduce((first, next) -> first + "|" + next).orElse("ok");
    }

    record SmallJson(int number) {}

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
