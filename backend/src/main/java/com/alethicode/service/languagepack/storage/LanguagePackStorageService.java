package com.alethicode.service.languagepack.storage;

import com.alethicode.config.AlethicodeProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Stream;

@Service
public class LanguagePackStorageService {

    private final Path storageRoot;
    private final Path previewDir;

    public LanguagePackStorageService(AlethicodeProperties properties) {
        this.storageRoot = Path.of(properties.getLanguagePack().getStorageRoot());
        this.previewDir = Path.of(properties.getLanguagePack().getPreviewDir());
    }

    public Path taskDir(Long taskId) {
        return storageRoot.resolve("tasks").resolve(String.valueOf(taskId));
    }

    public Path originalsDir(Long taskId) {
        return taskDir(taskId).resolve("originals");
    }

    public Path canonicalDir(Long taskId) {
        return taskDir(taskId).resolve("canonical");
    }

    public Path previewDir(Long taskId) {
        return previewDir.resolve("tasks").resolve(String.valueOf(taskId));
    }

    public StoredFile storeOriginal(Long taskId, MultipartFile file) throws IOException {
        Path dir = originalsDir(taskId);
        Files.createDirectories(dir);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "unnamed";
        }
        String sanitizedFilename = Path.of(originalFilename).getFileName().toString();
        if (sanitizedFilename.isBlank() || sanitizedFilename.startsWith(".")) {
            sanitizedFilename = "unnamed";
        }
        Path target = dir.resolve(sanitizedFilename);
        if (!target.normalize().startsWith(dir.normalize())) {
            throw new IllegalStateException("Path traversal detected in uploaded filename: " + originalFilename);
        }

        String hash;
        try (InputStream is = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            hash = HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }

        try (InputStream is = file.getInputStream()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return new StoredFile(target, hash, file.getSize());
    }

    public Path storeCanonical(Long taskId, Path sourceFile, String targetFilename) throws IOException {
        Path dir = canonicalDir(taskId);
        Files.createDirectories(dir);
        Path target = dir.resolve(targetFilename);
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public Path storePreview(Long taskId, Path previewPdf, String targetFilename) throws IOException {
        Path dir = previewDir(taskId);
        Files.createDirectories(dir);
        Path target = dir.resolve(targetFilename);
        Files.copy(previewPdf, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public void deleteTaskArtifacts(Long taskId) {
        deleteDirectoryIfExists(taskDir(taskId));
        deleteDirectoryIfExists(previewDir(taskId));
    }

    private void deleteDirectoryIfExists(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to delete language pack directory: " + directory, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to traverse language pack directory: " + directory, e);
        }
    }

    public record StoredFile(Path path, String hash, long sizeBytes) {
    }
}
