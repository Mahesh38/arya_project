package com.smartgrievance.common.web;

import java.beans.PropertyEditorSupport;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class FormBindingAdvice {
    private static final Pattern WHITE_SPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");

    @InitBinder
    void bind(WebDataBinder binder, HttpServletRequest request) {
        if (binder.getTarget() == null) {
            Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            String name = binder.getObjectName();
            if (!(variables instanceof Map<?, ?> paths) || !paths.containsKey(name)) {
                binder.registerCustomEditor(String.class, editor(normalizer(name)));
            }
            return;
        }
        binder.setDisallowedFields("files");
        binder.registerCustomEditor(String.class, editor(FormBindingAdvice::singleLine));
        for (String field : new String[]{"description", "remark", "changeReason", "password", "confirmPassword",
                "currentPassword", "newPassword", "email", "mobile", "original", "originalNo", "code", "q"}) {
            binder.registerCustomEditor(String.class, field, editor(normalizer(field)));
        }
    }

    private static UnaryOperator<String> normalizer(String name) {
        return switch (name) {
            case "description", "remark", "changeReason" -> FormBindingAdvice::multiLine;
            case "password", "confirmPassword", "currentPassword", "newPassword" -> UnaryOperator.identity();
            case "email" -> value -> caseAdjusted(value, false);
            case "mobile" -> FormBindingAdvice::mobile;
            case "original", "originalNo" -> value -> caseAdjusted(value, true);
            case "code" -> FormBindingAdvice::withoutSpaces;
            case "q" -> FormBindingAdvice::search;
            default -> FormBindingAdvice::singleLine;
        };
    }

    private static String search(String value) {
        String normalized = singleLine(value);
        if (normalized == null || normalized.length() <= 100) return normalized;
        int end = Character.isHighSurrogate(normalized.charAt(99)) ? 99 : 100;
        return normalized.substring(0, end);
    }

    static String singleLine(String value) {
        String normalized = multiLine(value);
        return normalized == null ? null : emptyToNull(WHITE_SPACE.matcher(normalized).replaceAll(" ").strip());
    }

    static String multiLine(String value) {
        if (value == null) return null;
        return emptyToNull(CONTROL.matcher(value.replace("\r\n", "\n").replace('\r', '\n'))
                .replaceAll("").strip());
    }

    private static String emptyToNull(String value) { return value.isEmpty() ? null : value; }

    private static String withoutSpaces(String value) {
        String normalized = singleLine(value);
        return normalized == null ? null : normalized.replace(" ", "");
    }

    private static String caseAdjusted(String value, boolean upper) {
        String normalized = withoutSpaces(value);
        if (normalized == null) return null;
        return upper ? normalized.toUpperCase(Locale.ROOT) : normalized.toLowerCase(Locale.ROOT);
    }

    static String mobile(String value) {
        String normalized = singleLine(value);
        if (normalized == null) return null;
        String digits = normalized.replaceAll("[ .()\\-]", "");
        if (digits.length() == 13 && digits.startsWith("+91")) return digits.substring(3);
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) return digits.substring(1);
        return emptyToNull(digits);
    }

    private static PropertyEditorSupport editor(UnaryOperator<String> normalize) {
        return new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) { setValue(normalize.apply(text)); }
        };
    }
}
