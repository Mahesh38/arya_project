package com.smartgrievance.common.web;

import java.util.Map;

import com.smartgrievance.common.exception.ActionRefusedException;
import com.smartgrievance.common.exception.BusinessRuleException;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.config.AppProperties;
import com.smartgrievance.user.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Logger SECURITY = LoggerFactory.getLogger("SECURITY");
    private final Flash flash;
    private final AppProperties properties;

    public GlobalExceptionHandler(Flash flash, AppProperties properties) {
        this.flash = flash;
        this.properties = properties;
    }

    @ExceptionHandler(NotFoundException.class)
    ModelAndView notFound(NotFoundException ex, HttpServletRequest request) {
        LOG.info("NOT_FOUND object={} ref={}", ex.object(), ex.ref());
        if (ex.policy()) {
            CurrentUser actor = request.getUserPrincipal() instanceof Authentication authentication
                    && authentication.getPrincipal() instanceof CurrentUser user ? user : null;
            SECURITY.info("NOT_VISIBLE object={} ref={} userId={} role={}", ex.object(), ex.ref(),
                    actor == null ? null : actor.id(), actor == null ? null : actor.role());
        }
        return ErrorPages.view(HttpStatus.NOT_FOUND, request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ModelAndView missingResource(NoResourceFoundException failure, HttpServletRequest request) {
        return clientError(HttpStatus.NOT_FOUND, failure, request);
    }

    @ExceptionHandler(ActionRefusedException.class)
    String refused(ActionRefusedException ex, HttpServletRequest request) {
        String rule = ex instanceof BusinessRuleException business ? business.rule() : "";
        LOG.info("REFUSED code={} rule={} key={} subject={}", ex.code(), rule, ex.messageKey(), ex.subject());
        flash.error(request, ex.messageKey(), ex.args());
        return "redirect:" + RedirectTargets.of(ex.subject(), request);
    }

    @ExceptionHandler({TypeMismatchException.class, ServletRequestBindingException.class,
            MissingServletRequestPartException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, MultipartException.class})
    ModelAndView invalidRequest(Exception failure, HttpServletRequest request) {
        if (failure instanceof MethodArgumentTypeMismatchException mismatch
                && mismatch.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return clientError(HttpStatus.NOT_FOUND, failure, request);
        }
        return clientError(HttpStatus.BAD_REQUEST, failure, request);
    }

    @ExceptionHandler(BindException.class)
    ModelAndView unhandledBindingError(HttpServletRequest request) {
        LOG.warn("BIND_WITHOUT_BINDING_RESULT path={}", request.getRequestURI());
        return ErrorPages.view(HttpStatus.BAD_REQUEST, request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ModelAndView invalidMethod(HttpRequestMethodNotSupportedException failure,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Allow", failure.getHeaders().getFirst("Allow"));
        return clientError(HttpStatus.METHOD_NOT_ALLOWED, failure, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ModelAndView unsupportedMediaType(HttpMediaTypeNotSupportedException failure, HttpServletRequest request) {
        return clientError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, failure, request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ModelAndView notAcceptable(HttpMediaTypeNotAcceptableException failure, HttpServletRequest request) {
        return clientError(HttpStatus.NOT_ACCEPTABLE, failure, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ModelAndView uploadTooLarge(HttpServletRequest request) {
        SECURITY.warn("UPLOAD_REJECTED reason=request_too_large");
        var destination = RedirectTargets.uploadForm(request);
        if (destination.isPresent()) {
            if (request.getSession(false) != null) {
                long perFileMb = properties.upload().maxFileSize().toMegabytes();
                flash.error(request, "error.upload.requestTooLarge", perFileMb,
                        perFileMb * properties.upload().maxFilesPerComplaint());
            }
            return new ModelAndView("redirect:" + destination.get());
        }
        return ErrorPages.view(HttpStatus.CONTENT_TOO_LARGE, request, null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void securityFailure(RuntimeException failure) {
        throw failure;
    }

    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    void disconnected(Exception failure, HttpServletRequest request, HttpServletResponse response) {
        logClientError(response.getStatus(), failure, request);
    }

    @ExceptionHandler(Exception.class)
    Object unexpected(Exception failure, HttpServletRequest request) {
        String reference = ErrorPages.reference(request, failure);
        if ("GET".equals(request.getMethod())
                && (request.getContextPath() + "/notifications/unread-count").equals(request.getRequestURI())) {
            return ResponseEntity.internalServerError().body(Map.of("error", "server_error", "reference", reference));
        }
        return ErrorPages.view(HttpStatus.INTERNAL_SERVER_ERROR, request, reference);
    }

    private static ModelAndView clientError(HttpStatus status, Exception failure, HttpServletRequest request) {
        logClientError(status.value(), failure, request);
        return ErrorPages.view(status, request, null);
    }

    private static void logClientError(int status, Exception failure, HttpServletRequest request) {
        LOG.debug("CLIENT_ERROR status={} type={} path={}", status, failure.getClass().getSimpleName(), request.getRequestURI());
    }
}
