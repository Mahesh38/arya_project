package com.smartgrievance.attachment;

import com.smartgrievance.user.CurrentUser;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class AttachmentController {
    private final AttachmentService attachments;

    AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping("/attachments/{id}")
    ResponseEntity<InputStreamResource> download(@PathVariable long id, @RequestParam(defaultValue = "false") boolean download,
                                                 @AuthenticationPrincipal CurrentUser user) {
        var file = attachments.load(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(file.originalName(), download))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.body());
    }

    private static String disposition(String name, boolean download) {
        String ascii = name.replaceAll("[^\\x20-\\x7E]|[\"\\\\]", "_");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
        return (download ? "attachment" : "inline") + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
