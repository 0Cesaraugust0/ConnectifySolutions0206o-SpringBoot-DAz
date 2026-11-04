package com.connectify.controller;

import com.connectify.entity.Event;
import com.connectify.entity.EventStatus;
import com.connectify.repository.EventRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeoControllerTest {

    @Test
    void robotsReferencesThePublicSitemapAndProtectsPrivateAreas() {
        SeoController controller = new SeoController(mock(EventRepository.class), "https://www.showyouplay.com/");

        String robots = controller.robots().getBody();

        assertThat(robots)
                .contains("Sitemap: https://www.showyouplay.com/sitemap.xml")
                .contains("Allow: /events")
                .contains("Disallow: /dashboard/")
                .doesNotContain("Disallow: /events");
    }

    @Test
    void sitemapContainsOnlyPublishedEventsReturnedByTheRepository() {
        EventRepository repository = mock(EventRepository.class);
        Event published = new Event();
        published.setTitle("Evento público");
        published.setStatus(EventStatus.PUBLISHED);
        published.setUpdatedAt(LocalDateTime.of(2026, 11, 4, 10, 30));
        setEventId(published, 21L);
        when(repository.findByStatusOrderByUpdatedAtDesc(EventStatus.PUBLISHED)).thenReturn(List.of(published));

        String sitemap = new SeoController(repository, "https://www.showyouplay.com").sitemap().getBody();

        assertThat(sitemap)
                .contains("<loc>https://www.showyouplay.com/events</loc>")
                .contains("<loc>https://www.showyouplay.com/events/21</loc>")
                .contains("<lastmod>2026-11-04</lastmod>");
    }

    private void setEventId(Event event, Long id) {
        try {
            var field = Event.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(event, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
