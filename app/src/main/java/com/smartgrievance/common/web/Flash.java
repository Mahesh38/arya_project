package com.smartgrievance.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

@Component
public class Flash {
    private final MessageSource messages;

    public Flash(MessageSource messages) {
        this.messages = messages;
    }

    public void success(RedirectAttributes redirect, String key, Object... args) {
        add(redirect, "flashSuccess", key, args);
    }

    public void error(RedirectAttributes redirect, String key, Object... args) {
        add(redirect, "flashError", key, args);
    }

    void error(HttpServletRequest request, String key, Object... args) {
        RequestContextUtils.getOutputFlashMap(request)
                .put("flashError", messages.getMessage(key, args, LocaleContextHolder.getLocale()));
    }

    public void info(RedirectAttributes redirect, String key, Object... args) {
        add(redirect, "flashInfo", key, args);
    }

    public void info(HttpServletRequest request, HttpServletResponse response, String target, String key) {
        RequestContextUtils.getOutputFlashMap(request)
                .put("flashInfo", messages.getMessage(key, null, LocaleContextHolder.getLocale()));
        RequestContextUtils.saveOutputFlashMap(target, request, response);
    }

    private void add(RedirectAttributes redirect, String attribute, String key, Object[] args) {
        redirect.addFlashAttribute(attribute, messages.getMessage(key, args, LocaleContextHolder.getLocale()));
    }
}
