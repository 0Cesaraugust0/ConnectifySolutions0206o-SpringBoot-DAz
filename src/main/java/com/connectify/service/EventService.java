package com.connectify.service;

import com.connectify.entity.Event;
import com.connectify.entity.Category;
import com.connectify.entity.EventStatus;
import com.connectify.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<Event> findPublished(String query, String city, String category) {
        return findPublished(query, city, category, null);
    }

    public List<Event> findPublished(String query, String city, String category, String dateRange) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = resolveEndDate(now, dateRange);

        return eventRepository.findByStatusAndEventDateAfterOrderByEventDateAsc(EventStatus.PUBLISHED, now).stream()
                .filter(event -> matchesQuery(event, query))
                .filter(event -> matchesText(event.getCity(), city))
                .filter(event -> matchesCategory(event.getCategory(), category))
                .filter(event -> endDate == null || !event.getEventDate().isAfter(endDate))
                .toList();
    }

    public List<Event> findAll() {
        return eventRepository.findAll();
    }

    public Optional<Event> findById(Long id) {
        return eventRepository.findById(id);
    }

    public Event save(Event event) {
        return eventRepository.save(event);
    }

    private boolean matchesQuery(Event event, String query) {
        if (!StringUtils.hasText(query)) return true;
        return contains(event.getTitle(), query)
                || contains(event.getDescription(), query)
                || contains(event.getOrganizerName(), query);
    }

    private boolean matchesCategory(Category category, String expected) {
        if (!StringUtils.hasText(expected)) return true;
        if (category == null) return false;
        if ("Música".equalsIgnoreCase(expected.trim())) return category.isMusic();
        return category.getName() != null && category.getName().equalsIgnoreCase(expected.trim());
    }

    private boolean matchesText(String value, String expected) {
        if (!StringUtils.hasText(expected)) return true;
        return contains(value, expected);
    }

    private boolean contains(String value, String expected) {
        if (!StringUtils.hasText(expected)) return true;
        return value != null && value.toLowerCase().contains(expected.trim().toLowerCase());
    }

    private LocalDateTime resolveEndDate(LocalDateTime now, String dateRange) {
        if (!StringUtils.hasText(dateRange)) return null;
        return switch (dateRange.trim().toLowerCase()) {
            case "today" -> now.toLocalDate().atTime(LocalTime.MAX);
            case "week" -> now.plusDays(7);
            case "month" -> now.toLocalDate().withDayOfMonth(now.toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);
            case "next2months" -> now.plusMonths(2);
            default -> null;
        };
    }
}
