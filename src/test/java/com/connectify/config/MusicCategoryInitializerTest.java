package com.connectify.config;

import com.connectify.entity.Category;
import com.connectify.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MusicCategoryInitializerTest {
    @Test
    void initializesOnlyMissingSpecialtiesAndDoesNotDuplicateThemOnRestart() {
        CategoryRepository repository = mock(CategoryRepository.class);
        when(repository.findByNameIgnoreCase("Artistas Cover"))
                .thenReturn(Optional.empty(), Optional.of(new Category("Artistas Cover", "", "")));
        when(repository.findByNameIgnoreCase("Artistas Yo Soy"))
                .thenReturn(Optional.empty(), Optional.of(new Category("Artistas Yo Soy", "", "")));
        MusicCategoryInitializer initializer = new MusicCategoryInitializer(repository);
        initializer.run();
        initializer.run();
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Category::getName)
                .containsExactly("Artistas Cover", "Artistas Yo Soy");
        assertThat(saved.getAllValues()).allMatch(Category::isMusic).allMatch(Category::isActive);
    }

    @Test
    void keepsExistingCatalogEntriesAndTheirSettings() {
        CategoryRepository repository = mock(CategoryRepository.class);
        Category existing = new Category("artistas cover", "original", "Descripción original");
        existing.setActive(false);
        when(repository.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(existing));
        new MusicCategoryInitializer(repository).run();
        verify(repository, never()).save(any());
        assertThat(existing.getDescription()).isEqualTo("Descripción original");
        assertThat(existing.isActive()).isFalse();
    }
}
