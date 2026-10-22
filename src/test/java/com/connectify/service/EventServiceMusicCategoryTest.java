package com.connectify.service;

import com.connectify.entity.Category;
import com.connectify.entity.Event;
import com.connectify.entity.EventStatus;
import com.connectify.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventServiceMusicCategoryTest {
    private final EventRepository repository = mock(EventRepository.class);
    private final EventService service = new EventService(repository);
    private final Event general = event("Concierto", "Música", "Lima", 2);
    private final Event cover = event("Tributo en vivo", "Artistas Cover", "Lima", 3);
    private final Event yoSoy = event("Gala musical", "Artistas Yo Soy", "Tacna", 10);
    private final Event food = event("Artistas Cover invitados", "Gastronomía", "Lima", 4);

    @BeforeEach
    void catalog() {
        when(repository.findByStatusAndEventDateAfterOrderByEventDateAsc(eq(EventStatus.PUBLISHED), any()))
                .thenReturn(List.of(general, cover, food, yoSoy));
    }

    @Test
    void musicIncludesBothSpecialtiesAndKeepsOtherCategoriesSeparate() {
        assertThat(service.findPublished(null, null, "Música", null)).containsExactly(general, cover, yoSoy);
        assertThat(service.findPublished(null, null, "Gastronomía", null)).containsExactly(food);
    }

    @Test
    void specialtyUsesSavedCategoryInsteadOfWordsInTheTitle() {
        assertThat(service.findPublished(null, null, " artistas cover ", null)).containsExactly(cover);
        assertThat(service.findPublished(null, null, "Artistas Yo Soy", null)).containsExactly(yoSoy);
    }

    @Test
    void specialtiesRespectSearchCityAndDateTogether() {
        assertThat(service.findPublished("tributo", "lima", "Artistas Cover", "week")).containsExactly(cover);
        assertThat(service.findPublished(null, "Lima", "Artistas Yo Soy", null)).isEmpty();
        assertThat(service.findPublished(null, null, "Artistas Yo Soy", "week")).isEmpty();
        assertThat(service.findPublished(null, null, "Artistas Yo Soy", "next2months")).containsExactly(yoSoy);
        assertThat(service.findPublished(null, null, null, null)).containsExactly(general, cover, food, yoSoy);
    }

    private static Event event(String title, String category, String city, int days) {
        Event event = new Event();
        event.setTitle(title);
        event.setCategory(new Category(category, "", ""));
        event.setCity(city);
        event.setEventDate(LocalDateTime.now().plusDays(days));
        return event;
    }
}
