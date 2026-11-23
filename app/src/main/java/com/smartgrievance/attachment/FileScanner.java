package com.smartgrievance.attachment;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

@FunctionalInterface
public interface FileScanner {
    void scan(MultipartFile file) throws IOException;
}
