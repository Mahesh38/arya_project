package com.smartgrievance.attachment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.StorageException;
import com.smartgrievance.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {
    private static final byte[] PNG = HexFormat.of().parseHex("89504E470D0A1A0A");
    private static final long MEGABYTE = 1024 * 1024;
    @TempDir Path uploads;

    static Stream<Arguments> fileSignatures() {
        return Stream.of(Arguments.of("FFD8FF", FileTypeDetector.Type.JPEG),
                Arguments.of("89504E470D0A1A0A", FileTypeDetector.Type.PNG),
                Arguments.of("255044462D", FileTypeDetector.Type.PDF),
                Arguments.of("474946383961", null), Arguments.of("89504E", null),
                Arguments.of("54657874206F6E6C79", null), Arguments.of("", null));
    }

    @ParameterizedTest
    @MethodSource("fileSignatures")
    @DisplayName("TC-BR-05 attachment types are selected by their complete content signature")
    void detectsDeclaredFormats(String hex, FileTypeDetector.Type expected) throws IOException {
        try (var input = new ByteArrayInputStream(HexFormat.of().parseHex(hex))) {
            assertThat(FileTypeDetector.detect(input).orElse(null)).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("TC-BR-07 storage generates safe paths and cleans original names")
    void storesFilesUnderTheUploadRoot() throws Exception {
        var service = service(file -> {});
        var written = service.store(List.of(image("photos/field-shot.png"), image("a\"b<c>.png"),
                image("प".repeat(300) + ".png")));

        assertThat(written).extracting(FileStorageService.StoredFile::originalName)
                .containsExactly("field-shot.png", "abc.png", "प".repeat(255));
        for (var file : written) {
            assertThat(file.storedName()).matches("2026/11/[0-9a-f-]{36}\\.png");
            assertThat(file.contentType()).isEqualTo("image/png");
            Path stored = service.resolveForRead(file.storedName());
            assertThat(stored).startsWith(uploads.toRealPath());
            assertThat(Files.readAllBytes(stored)).isEqualTo(PNG);
            if (Files.getFileStore(stored).supportsFileAttributeView("posix")) {
                assertThat(java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(stored)))
                        .isEqualTo("rw-r-----");
            }
        }
    }

    @Test
    @DisplayName("The validator keeps the count message first and retains every invalid file message")
    void collectsCountsSizesAndTypes() throws IOException {
        var service = service(file -> {});
        var parts = new ArrayList<MultipartFile>();
        for (int n = 0; n < 5; n++) parts.add(image("photo-" + n + ".png"));
        parts.add(new MockMultipartFile("files", "photos/large.png", "image/png", new byte[(int) (6 * MEGABYTE)]));
        parts.add(new MockMultipartFile("files", "photos/empty.png", "image/png", new byte[0]));
        parts.add(new MockMultipartFile("files", "", "application/octet-stream", new byte[0]));

        var errors = service.validate(parts, AttachmentPurpose.COMPLAINT, 0, 0);
        assertThat(errors).extracting(FormError::key)
                .containsExactly("validation.files.tooMany", "validation.files.tooLarge", "validation.files.type");
        assertThat(errors.getFirst().args()).containsExactly(5, 7);
        assertThat(errors.get(1).args()).containsExactly("large.png", 5L);
        assertThat(errors.getLast().args()).containsExactly("empty.png");
    }

    @Test
    @DisplayName("TC-BR-06 attachment count uses the post-edit allocation and the resolution limit")
    void countsActiveAndResolutionFiles() throws IOException {
        var service = service(file -> {});
        var photo = image("photo.png");
        assertThat(service.validateEdit(List.of(photo), AttachmentPurpose.COMPLAINT, 5, 1)).isEmpty();
        assertThat(service.validateEdit(List.of(photo), AttachmentPurpose.COMPLAINT, 5, 0))
                .singleElement().satisfies(error -> {
                    assertThat(error.key()).isEqualTo("validation.files.tooManyAfterEdit");
                    assertThat(error.args()).containsExactly(5, 5, 1);
                });
        assertThat(service.validate(List.of(photo, photo, photo), AttachmentPurpose.RESOLUTION, 0, 0)).isEmpty();
        assertThat(service.validate(List.of(photo, photo, photo, photo), AttachmentPurpose.RESOLUTION, 0, 0))
                .singleElement().extracting(FormError::key).isEqualTo("validation.files.tooManyResolution");
    }

    @Test
    @DisplayName("A write failure removes previously stored files from the same operation")
    void removesFilesWhenStorageFails() throws Exception {
        var calls = new AtomicInteger();
        var service = service(file -> {
            if (calls.incrementAndGet() == 2) throw new IOException("test scanner failure");
        });

        assertThatThrownBy(() -> service.store(List.of(image("first.png"), image("second.png"))))
                .isInstanceOf(StorageException.class);
        try (var paths = Files.walk(uploads)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    @DisplayName("The storage cleanup hook removes all files after a transaction rollback")
    void removesFilesAfterRollback() throws Exception {
        var service = service(file -> {});
        TransactionSynchronizationManager.initSynchronization();
        try {
            var files = service.store(List.of(image("first.png"), image("second.png")));
            for (var file : files) assertThat(service.resolveForRead(file.storedName())).exists();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(hook -> hook.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            try (var paths = Files.walk(uploads)) {
                assertThat(paths.filter(Files::isRegularFile)).isEmpty();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private FileStorageService service(FileScanner scanner) throws IOException {
        var upload = new AppProperties.Upload(uploads, DataSize.ofMegabytes(5), 5,
                List.of("image/jpeg", "image/png", "application/pdf"));
        var config = new AppProperties(null, null, null, upload, null, null, null, null);
        return new FileStorageService(config,
                Clock.fixed(Instant.parse("2026-11-23T04:30:00Z"), ZoneId.of("Asia/Kolkata")), scanner);
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("files", name, "application/octet-stream", PNG);
    }
}
