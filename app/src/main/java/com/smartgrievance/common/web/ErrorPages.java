package com.smartgrievance.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.servlet.ModelAndView;

final class ErrorPages {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorPages.class);

    private ErrorPages() {}

    static ModelAndView view(HttpStatusCode status, HttpServletRequest request, String reference) {
        int code = status.value();
        String variant = switch (code) {
            case 400, 403, 404, 405, 413 -> Integer.toString(code);
            default -> status.is5xxServerError() ? "500" : "4xx";
        };
        String name = switch (variant) {
            case "403", "404", "500" -> variant;
            default -> "4xx";
        };
        if (code == 403 && "csrf".equals(request.getAttribute("app.errorReason"))) variant += ".csrf";
        String prefix = "page.error." + variant;
        ModelAndView view = new ModelAndView("error/" + name);
        view.setStatus(status);
        view.addObject("status", code);
        view.addObject("titleKey", prefix + ".title");
        view.addObject("textKey", prefix + ".text");
        view.addObject("reference", reference);
        if (request.getUserPrincipal() != null) view.addObject("homeUrl", "/");
        return view;
    }

    static String reference(HttpServletRequest request, Throwable failure) {
        if (request.getAttribute(ErrorReference.ATTRIBUTE) instanceof String existing) return existing;

        String created = ErrorReference.next();
        request.setAttribute(ErrorReference.ATTRIBUTE, created);
        try (var ignored = MDC.putCloseable("ref", created)) {
            LOG.error("UNEXPECTED ref={} method={} path={}", created, request.getMethod(), request.getRequestURI(), failure);
        }
        return created;
    }
}
