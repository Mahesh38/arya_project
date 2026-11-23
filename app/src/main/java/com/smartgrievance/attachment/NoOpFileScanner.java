package com.smartgrievance.attachment;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
final class NoOpFileScanner implements FileScanner {
    @Override
    public void scan(MultipartFile file) {
        // The scan hook is reserved for an antivirus implementation.
    }
}
