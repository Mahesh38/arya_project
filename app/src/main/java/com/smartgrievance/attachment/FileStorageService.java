package com.smartgrievance.attachment;

import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.StorageException;
import com.smartgrievance.config.AppProperties;
import com.smartgrievance.security.SecurityEvents;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private static final Logger LOG = LoggerFactory.getLogger(FileStorageService.class);
    private static final DateTimeFormatter DIRECTORY = DateTimeFormatter.ofPattern("uuuu/MM");
    private static final Pattern UNSAFE_NAME = Pattern.compile("[\\p{Cc}\"*:<>?|]");
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final int RESOLUTION_LIMIT = 3;

    private final AppProperties.Upload config;
    private final Clock clock;
    private final FileScanner scanner;
    private final Path root;

    public FileStorageService(AppProperties properties, Clock clock, FileScanner scanner) throws IOException {
        this.config = properties.upload();
        this.clock = clock;
        this.scanner = scanner;
        Path configured = config.dir().toAbsolutePath().normalize();
        boolean created = Files.notExists(configured, LinkOption.NOFOLLOW_LINKS);
        root = Files.createDirectories(configured, permissions(configured, "rwxr-x---")).toRealPath();
        if (created) setPermissions(root, "rwxr-x---");
    }

    public List<FormError> validate(List<MultipartFile> files, AttachmentPurpose purpose,
                                    int activeCount, int removedCount) {
        return validate(files, purpose, activeCount, removedCount, false, null);
    }

    public List<FormError> validate(List<MultipartFile> files, AttachmentPurpose purpose,
                                    int activeCount, int removedCount, long actorId) {
        return validate(files, purpose, activeCount, removedCount, false, actorId);
    }

    public List<FormError> validateEdit(List<MultipartFile> files, AttachmentPurpose purpose,
                                        int activeCount, int removedCount) {
        return validate(files, purpose, activeCount, removedCount, true, null);
    }

    public List<FormError> validateEdit(List<MultipartFile> files, AttachmentPurpose purpose,
                                        int activeCount, int removedCount, long actorId) {
        return validate(files, purpose, activeCount, removedCount, true, actorId);
    }

    private List<FormError> validate(List<MultipartFile> files, AttachmentPurpose purpose, int activeCount,
                                     int removedCount, boolean editing, Long actorId) {
        var acceptedParts = selected(files);
        int remaining = activeCount - removedCount;
        int limit = purpose == AttachmentPurpose.RESOLUTION ? RESOLUTION_LIMIT : config.maxFilesPerComplaint();
        var errors = new ArrayList<FormError>();
        if (remaining + acceptedParts.size() > limit) {
            errors.add(countError(purpose, editing, limit, remaining, acceptedParts.size()));
            SecurityEvents.LOG.warn("UPLOAD_REJECTED userId={} reason=count", actorId);
        }
        for (MultipartFile file : acceptedParts) {
            String name = cleanName(file.getOriginalFilename());
            if (file.getSize() > config.maxFileSize().toBytes()) {
                errors.add(new FormError("files", "validation.files.tooLarge", name, config.maxFileSize().toMegabytes()));
                SecurityEvents.LOG.warn("UPLOAD_REJECTED userId={} reason=size", actorId);
            } else if (type(file).isEmpty()) {
                errors.add(new FormError("files", "validation.files.type", name));
                SecurityEvents.LOG.warn("UPLOAD_REJECTED userId={} reason=type", actorId);
            }
        }
        return List.copyOf(errors);
    }

    private static FormError countError(AttachmentPurpose purpose, boolean editing, int limit, int remaining, int selected) {
        if (purpose == AttachmentPurpose.RESOLUTION) {
            return new FormError("files", "validation.files.tooManyResolution", limit, selected);
        }
        return editing ? new FormError("files", "validation.files.tooManyAfterEdit", limit, remaining, selected)
                : new FormError("files", "validation.files.tooMany", limit, selected);
    }

    List<StoredFile> store(List<MultipartFile> files) {
        var written = new ArrayList<StoredFile>();
        try {
            for (MultipartFile part : selected(files)) {
                written.add(storeOne(part));
            }
        } catch (IOException | RuntimeException failure) {
            cleanup(written);
            if (failure instanceof StorageException storage) throw storage;
            throw new StorageException(failure);
        }
        var result = List.copyOf(written);
        if (!result.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) cleanup(result);
                }
            });
        }
        return result;
    }

    private StoredFile storeOne(MultipartFile part) throws IOException {
        var type = type(part).orElseThrow(() -> new IOException("Attachment content is not allowed"));
        scanner.scan(part);
        String storedName = DIRECTORY.format(YearMonth.now(clock)) + "/" + UUID.randomUUID() + "." + type.extension();
        Path path = resolve(storedName);
        createChildDirectory(path.getParent().getParent());
        createChildDirectory(path.getParent());

        boolean owned = false;
        try (InputStream input = part.getInputStream();
             var channel = Files.newByteChannel(path, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                     permissions(root, "rw-r-----"))) {
            owned = true;
            long size = copy(input, channel);
            setPermissions(path, "rw-r-----");
            return new StoredFile(cleanName(part.getOriginalFilename()), storedName, type.mediaType(), size);
        } catch (IOException | RuntimeException failure) {
            if (owned) delete(path);
            throw failure;
        }
    }

    private void createChildDirectory(Path directory) throws IOException {
        if (!directory.getParent().toRealPath().startsWith(root)) {
            throw new IOException("Attachment directory is outside storage");
        }
        boolean created = Files.notExists(directory, LinkOption.NOFOLLOW_LINKS);
        Path real = Files.createDirectories(directory, permissions(root, "rwxr-x---")).toRealPath();
        if (!real.startsWith(root)) throw new IOException("Attachment directory is outside storage");
        if (created) setPermissions(real, "rwxr-x---");
    }

    private long copy(InputStream input, java.nio.channels.WritableByteChannel channel) throws IOException {
        var output = Channels.newOutputStream(channel);
        byte[] buffer = new byte[8192];
        long size = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            size += read;
            if (size > config.maxFileSize().toBytes()) throw new IOException("Attachment exceeds the configured limit");
            output.write(buffer, 0, read);
        }
        if (size == 0) throw new IOException("Attachment is empty");
        return size;
    }

    public Path resolveForRead(String storedName) throws IOException {
        Path path = resolve(storedName).toRealPath();
        if (!path.startsWith(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Attachment is outside storage or is not a regular file");
        }
        return path;
    }

    private Path resolve(String storedName) throws IOException {
        try {
            Path name = Path.of(storedName);
            Path path = root.resolve(name).normalize();
            if (name.isAbsolute() || !path.startsWith(root) || path.equals(root)) {
                throw new IOException("Attachment path is outside storage");
            }
            return path;
        } catch (InvalidPathException failure) {
            throw new IOException("Invalid attachment path", failure);
        }
    }

    private Optional<FileTypeDetector.Type> type(MultipartFile part) {
        try (InputStream input = part.getInputStream()) {
            return FileTypeDetector.detect(input).filter(type -> config.allowedTypes().contains(type.mediaType()));
        } catch (IOException failure) {
            throw new StorageException(failure);
        }
    }

    private static List<MultipartFile> selected(List<MultipartFile> parts) {
        return parts.stream().filter(part ->
                part.getSize() != 0 || !Optional.ofNullable(part.getOriginalFilename()).orElse("").isEmpty()).toList();
    }

    static String cleanName(String name) {
        if (name == null) return "file";
        String basename = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        String cleaned = SPACES.matcher(UNSAFE_NAME.matcher(basename).replaceAll("")).replaceAll(" ").strip();
        if (cleaned.isEmpty()) return "file";
        int count = cleaned.codePointCount(0, cleaned.length());
        return count <= 255 ? cleaned : cleaned.substring(0, cleaned.offsetByCodePoints(0, 255));
    }

    private static FileAttribute<?>[] permissions(Path path, String mode) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(mode))}
                : new FileAttribute<?>[0];
    }

    private static void setPermissions(Path path, String mode) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode));
        }
    }

    private void cleanup(List<StoredFile> files) {
        for (var file : files) {
            try {
                Path path = resolve(file.storedName());
                if (!path.getParent().toRealPath().startsWith(root)) {
                    throw new IOException("Attachment directory is outside storage");
                }
                delete(path);
            } catch (IOException failure) {
                LOG.error("ATTACHMENT_CLEANUP_FAILED", failure);
            }
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException failure) {
            LOG.error("ATTACHMENT_CLEANUP_FAILED", failure);
        }
    }

    record StoredFile(String originalName, String storedName, String contentType, long sizeBytes) {}
}
