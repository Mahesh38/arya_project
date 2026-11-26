package com.smartgrievance.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

public final class FileTypeDetector {
    private FileTypeDetector() {}

    public enum Type {
        JPEG("image/jpeg", "jpg", "FFD8FF"),
        PNG("image/png", "png", "89504E470D0A1A0A"),
        PDF("application/pdf", "pdf", "255044462D");

        private final String mediaType;
        private final String extension;
        private final byte[] signature;

        Type(String mediaType, String extension, String signature) {
            this.mediaType = mediaType;
            this.extension = extension;
            this.signature = HexFormat.of().parseHex(signature);
        }

        public String mediaType() { return mediaType; }
        public String extension() { return extension; }

        private boolean matches(byte[] start) {
            return start.length >= signature.length
                    && Arrays.equals(start, 0, signature.length, signature, 0, signature.length);
        }
    }

    public static Optional<Type> detect(InputStream input) throws IOException {
        byte[] start = input.readNBytes(8);
        return Arrays.stream(Type.values()).filter(type -> type.matches(start)).findFirst();
    }
}
