package com.smartgrievance.common.web;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

import com.smartgrievance.common.exception.Subject;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriUtils;

public final class RedirectTargets {
    private static final Pattern COMPLAINT_UPLOAD_FORM = Pattern.compile(
            "/(?:citizen/complaints/GRV-[0-9]{4}-[0-9]{6}/edit|"
                    + "(?:officer|admin)/complaints/GRV-[0-9]{4}-[0-9]{6}/resolve)");

    private RedirectTargets() {}

    public static String of(Subject subject, HttpServletRequest request) {
        return switch (subject.type()) {
            case COMPLAINT -> "/complaints/" + UriUtils.encodePathSegment(subject.ref(), StandardCharsets.UTF_8);
            case OTP -> "/verify-mobile";
            case EXPORT -> {
                String query = request.getQueryString();
                yield "/admin/complaints" + (query == null || query.isEmpty() ? "" : "?" + query);
            }
            case DEPARTMENTS -> "/admin/departments";
            case CATEGORIES -> "/admin/categories";
            case AREAS -> "/admin/areas";
            case OFFICERS -> "/admin/officers";
            case CITIZENS -> "/admin/citizens";
        };
    }

    public static Optional<String> uploadForm(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return Optional.empty();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/citizen/complaints")) return Optional.of("/citizen/complaints/new");
        return COMPLAINT_UPLOAD_FORM.matcher(path).matches() ? Optional.of(path) : Optional.empty();
    }
}
