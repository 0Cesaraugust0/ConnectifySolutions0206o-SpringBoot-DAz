package com.connectify.config;

import com.connectify.entity.Category;
import com.connectify.repository.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Shared category catalog, independent of demonstration events and accounts. */
@Component
public class MusicCategoryInitializer implements CommandLineRunner {
    private final CategoryRepository categories;

    public MusicCategoryInitializer(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    @Transactional
    public void run(String... args) {
        addIfMissing("Artistas Cover", "Presentaciones musicales de artistas y bandas cover.");
        addIfMissing("Artistas Yo Soy", "Presentaciones musicales de artistas de Yo Soy.");
    }

    private void addIfMissing(String name, String description) {
        if (categories.findByNameIgnoreCase(name).isEmpty()) {
            categories.save(new Category(name, "🎵", description));
        }
    }
}
