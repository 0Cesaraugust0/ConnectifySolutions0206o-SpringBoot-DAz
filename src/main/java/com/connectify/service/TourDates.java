package com.connectify.service;

import com.connectify.entity.Event;
import com.connectify.entity.EventPresentationSettings;
import com.connectify.entity.EventStatus;
import com.connectify.repository.EventPresentationSettingsRepository;
import com.connectify.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class TourDates {
    public static final int MAX_LINKS = 5;
    public static final Comparator<Event> CHRONOLOGICAL = Comparator
            .comparing(Event::getEventDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Event::getId);

    private TourDates() {}

    public record Route(Event origin, List<Event> nextDates) {}

    public static List<Long> ids(EventPresentationSettings settings) {
        if (settings == null || settings.getTourEventIds() == null) return List.of();
        return Arrays.stream(settings.getTourEventIds().split(","))
                .filter(value -> value.matches("[0-9]{1,18}"))
                .map(Long::valueOf).distinct().limit(MAX_LINKS).toList();
    }

    public static boolean canLink(Event source, Event target) {
        return !target.getId().equals(source.getId())
                && source.getOrganizerEmail() != null
                && source.getOrganizerEmail().equalsIgnoreCase(target.getOrganizerEmail())
                && source.getCity() != null && target.getCity() != null
                && !source.getCity().strip().equalsIgnoreCase(target.getCity().strip());
    }

    public static List<Event> visible(Event source, Iterable<Event> candidates, boolean includeUnpublished) {
        return java.util.stream.StreamSupport.stream(candidates.spliterator(), false)
                .filter(target -> canLink(source, target))
                .filter(target -> includeUnpublished || target.getStatus() == EventStatus.PUBLISHED)
                .sorted(CHRONOLOGICAL)
                .toList();
    }

    public static Route route(Event current, EventPresentationSettings currentSettings, boolean privateView,
                              EventRepository events, EventPresentationSettingsRepository settings) {
        List<Long> ownIds = ids(currentSettings);
        if (!ownIds.isEmpty()) {
            List<Event> linked = visible(current, events.findAllById(ownIds), privateView);
            if (!linked.isEmpty()) return new Route(current, upcoming(null, linked));
        }
        if (current.getOrganizerEmail() == null) return new Route(null, List.of());

        return settings.findByEvent_OrganizerEmailIgnoreCaseAndTourEventIdsIsNotNull(current.getOrganizerEmail()).stream()
                .filter(candidate -> ids(candidate).contains(current.getId()))
                .filter(candidate -> canLink(candidate.getEvent(), current))
                .filter(candidate -> privateView || candidate.getEvent().getStatus() == EventStatus.PUBLISHED)
                .min(Comparator.comparing(EventPresentationSettings::getEvent, CHRONOLOGICAL))
                .map(origin -> new Route(origin.getEvent(), upcoming(current,
                        visible(origin.getEvent(), events.findAllById(ids(origin)), privateView))))
                .orElseGet(() -> new Route(null, List.of()));
    }

    private static List<Event> upcoming(Event current, List<Event> dates) {
        LocalDateTime now = LocalDateTime.now();
        return dates.stream()
                .filter(date -> date.getEventDate() != null && !date.getEventDate().isBefore(now))
                .filter(date -> current == null || CHRONOLOGICAL.compare(date, current) > 0)
                .toList();
    }
}
