package com.smartgrievance.attachment;

import java.io.IOException;
import java.util.Locale;

import com.smartgrievance.common.web.RedirectTargets;
import com.smartgrievance.config.AppProperties;
import com.smartgrievance.security.SecurityEvents;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

final class UploadSizeGuardFilter extends OncePerRequestFilter {
    private final long maxRequestBytes;
    private final AppProperties.Upload upload;
    private final MessageSource messages;
    private final SessionFlashMapManager flashes = new SessionFlashMapManager();

    UploadSizeGuardFilter(long maxRequestBytes, AppProperties.Upload upload, MessageSource messages) {
        this.maxRequestBytes = maxRequestBytes;
        this.upload = upload;
        this.messages = messages;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() <= maxRequestBytes || !multipart(request.getContentType())) {
            chain.doFilter(request, response);
            return;
        }
        var form = RedirectTargets.uploadForm(request);
        if (form.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        String target = request.getContextPath() + form.get();
        if (request.getSession(false) != null) {
            long fileMiB = upload.maxFileSize().toMegabytes();
            var flash = new FlashMap();
            flash.put("flashError", messages.getMessage("error.upload.requestTooLarge",
                    new Object[]{fileMiB, fileMiB * upload.maxFilesPerComplaint()}, Locale.ENGLISH));
            flash.setTargetRequestPath(target);
            flashes.saveOutputFlashMap(flash, request, response);
        }
        SecurityEvents.LOG.warn("UPLOAD_REJECTED reason=request_too_large");
        response.sendRedirect(target);
    }

    private static boolean multipart(String contentType) {
        if (contentType == null) return false;
        int separator = contentType.indexOf(';');
        String mediaType = separator < 0 ? contentType : contentType.substring(0, separator);
        return mediaType.strip().equalsIgnoreCase("multipart/form-data");
    }
}
