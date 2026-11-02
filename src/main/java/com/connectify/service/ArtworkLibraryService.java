package com.connectify.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Read-only catalogue: discovering artwork never moves or changes the source files. */
@Service
public class ArtworkLibraryService {
    private static final Set<String> FORMATS = Set.of("png", "jpg", "jpeg", "webp", "gif", "svg");
    private final PathMatchingResourcePatternResolver resources = new PathMatchingResourcePatternResolver();
    private final Path presentationDirectory;

    @Autowired
    public ArtworkLibraryService(ImageStorageService storage) {
        this(storage.getUploadRoot().resolve("presentations"));
    }

    ArtworkLibraryService(Path presentationDirectory) {
        this.presentationDirectory = presentationDirectory;
    }

    public List<Artwork> list() throws IOException {
        Map<String, Artwork> catalogue = new LinkedHashMap<>();
        for (String pattern : List.of("classpath*:static/images/*.*", "classpath*:static/images/**/*.*")) {
            for (Resource resource : resources.getResources(pattern)) {
                String location = resource.getURL().toString();
                int root = location.lastIndexOf("static/images/");
                if (root < 0) continue;
                String url = "/images/" + location.substring(root + "static/images/".length());
                add(catalogue, url, resource.getFilename(), resource.contentLength(), "Diseños de la plataforma");
            }
        }
        if (Files.isDirectory(presentationDirectory, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> files = Files.list(presentationDirectory)) {
                for (Path file : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    String name = file.getFileName().toString();
                    // Existing uploads use UUID filenames. Do not publish arbitrary filesystem names.
                    if (name.matches("[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+")) {
                        add(catalogue, "/uploads/presentations/" + name, name, Files.size(file), "Imágenes de eventos");
                    }
                }
            }
        }
        Resource spider = resources.getResource("classpath:templates/fragments/halloween-spider-art.html");
        if (spider.exists()) {
            catalogue.put("spider", new Artwork("Arañita de Halloween", "", "Decoraciones", "SVG",
                    "Ilustración de la plataforma", "halloween-spider-art", spider.contentLength(), true));
        }
        return catalogue.values().stream().sorted(Comparator.comparing(Artwork::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private void add(Map<String, Artwork> catalogue, String url, String filename, long size, String origin) {
        if (filename == null || !filename.contains(".")) return;
        String format = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!FORMATS.contains(format)) return;
        String stem = filename.substring(0, filename.lastIndexOf('.'));
        String name = switch (stem) {
            case "ceremonial-tree-pennant" -> "Banderín ceremonial · Árbol";
            case "marketplace-hero" -> "Portada del Marketplace";
            case "halloween-party-couple" -> "Halloween · Baile en pareja";
            case "halloween-party-confetti" -> "Halloween · Lluvia de confeti";
            case "halloween-party-costumes" -> "Halloween · Fiesta de disfraces";
            default -> stem.replace('-', ' ').replace('_', ' ');
        };
        String category = stem.contains("stage") ? "Escenarios"
                : stem.contains("pennant") || stem.startsWith("halloween") ? "Decoraciones"
                : stem.contains("cover") || stem.contains("hero") ? "Portadas"
                : stem.contains("logo") || stem.contains("mark") || stem.startsWith("partner") ? "Identidad"
                : "Otras imágenes";
        catalogue.putIfAbsent(url, new Artwork(name, url, category, format.toUpperCase(Locale.ROOT), origin, filename, size, false));
    }

    public record Artwork(String name, String url, String category, String format, String origin,
                          String filename, long bytes, boolean illustration) {
        public String getName() { return name; }
        public String getUrl() { return url; }
        public String getCategory() { return category; }
        public String getFormat() { return format; }
        public String getOrigin() { return origin; }
        public String getFilename() { return filename; }
        public boolean isIllustration() { return illustration; }
        public String getSizeLabel() {
            return bytes < 1024 * 1024 ? String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
                    : String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        }
    }
}
