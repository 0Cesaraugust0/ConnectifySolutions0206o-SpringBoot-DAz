package com.connectify.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArtworkLibraryServiceTest {
    @TempDir Path uploads;

    @Test
    void discoversExistingArtworkAndExcludesVideosAndNonImageUploads() throws Exception {
        Files.write(uploads.resolve("sample.png"), new byte[]{1, 2, 3});
        Files.write(uploads.resolve("private.txt"), new byte[]{4});
        Files.write(uploads.resolve("party.mp4"), new byte[]{5});
        var library = new ArtworkLibraryService(uploads).list();
        assertThat(library).extracting(ArtworkLibraryService.Artwork::url)
                .contains("/uploads/presentations/sample.png", "/images/marketplace-hero.png", "/images/showcase/origen-cover.png")
                .doesNotHaveDuplicates().noneMatch(url -> url.endsWith(".mp4") || url.endsWith(".txt"));
        assertThat(library).anyMatch(art -> art.illustration() && art.name().contains("Arañita"));
        assertThat(Files.readAllBytes(uploads.resolve("sample.png"))).containsExactly(1, 2, 3);
        try (var files = Files.list(uploads)) { assertThat(files.count()).isEqualTo(3); }
    }

    @Test
    void stillShowsBundledArtworkWithoutAnUploadDirectory() throws Exception {
        assertThat(new ArtworkLibraryService(uploads.resolve("missing")).list()).isNotEmpty();
    }
}
