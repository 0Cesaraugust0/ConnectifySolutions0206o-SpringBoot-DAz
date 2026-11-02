package com.connectify.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class ImageStoragePersistenceTest {
    @TempDir Path root;

    @Test
    void uploadsSurviveServiceRecreationAndAppearInTheCatalogue() throws Exception {
        byte[] contents = "existing-image-fixture".getBytes(StandardCharsets.UTF_8);
        var image = new MockMultipartFile("imageFile", "portada.png", "image/png", contents);
        var storage = new ImageStorageService(root.toString());
        String first = storage.store(image, "presentations");
        var restartedStorage = new ImageStorageService(root.toString());
        String second = restartedStorage.store(image, "presentations");
        assertThat(first).isNotEqualTo(second);
        assertThat(Files.readAllBytes(root.resolve(first.substring("/uploads/".length())))).isEqualTo(contents);
        assertThat(Files.readAllBytes(root.resolve(second.substring("/uploads/".length())))).isEqualTo(contents);
        assertThat(new ArtworkLibraryService(restartedStorage).list()).extracting(ArtworkLibraryService.Artwork::url)
                .contains(first, second);
    }

    @Test
    void emptyOrRejectedUploadsDoNotCreateStoredFiles() throws Exception {
        var storage = new ImageStorageService(root.toString());
        assertThat(storage.store(new MockMultipartFile("imageFile", new byte[0]), "presentations")).isNull();
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("imageFile", "movie.mp4", "video/mp4", new byte[]{1}), "presentations"))
                .isInstanceOf(IllegalArgumentException.class);
        try (var files = Files.list(root)) { assertThat(files.count()).isZero(); }
    }
}
