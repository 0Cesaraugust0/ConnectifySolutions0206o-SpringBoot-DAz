package com.connectify.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final Path uploadRoot;

    public ImageStorageService() { this("uploads"); }

    @Autowired
    public ImageStorageService(@Value("${connectify.media.directory:uploads}") String directory) {
        this.uploadRoot = Paths.get(directory).toAbsolutePath().normalize();
    }

    public Path getUploadRoot() { return uploadRoot; }

    public String store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("La imagen no debe superar 5 MB.");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Solo se permiten imagenes JPG, PNG o WEBP.");
        }

        String safeFolder = folder == null ? "presentations" : folder.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safeFolder.isBlank()) {
            safeFolder = "presentations";
        }

        Path targetDir = uploadRoot.resolve(safeFolder).normalize();
        if (!targetDir.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Ruta de subida invalida.");
        }

        String extension = extensionFor(contentType);
        String filename = UUID.randomUUID() + extension;
        Path target = targetDir.resolve(filename).normalize();

        try {
            Files.createDirectories(targetDir);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la imagen.", ex);
        }

        return "/uploads/" + safeFolder + "/" + filename;
    }

    private String extensionFor(String contentType) {
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".jpg";
    }
}
