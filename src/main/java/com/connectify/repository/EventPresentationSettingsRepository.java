package com.connectify.repository;

import com.connectify.entity.EventPresentationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventPresentationSettingsRepository extends JpaRepository<EventPresentationSettings, Long> {
    Optional<EventPresentationSettings> findByEventId(Long eventId);
    List<EventPresentationSettings> findByEvent_IdIn(Collection<Long> eventIds);

    @EntityGraph(attributePaths = "event")
    List<EventPresentationSettings> findByEvent_OrganizerEmailIgnoreCaseAndTourEventIdsIsNotNull(String email);
}
