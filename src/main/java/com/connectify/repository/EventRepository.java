package com.connectify.repository;

import com.connectify.entity.Event;
import com.connectify.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findLockedById(@Param("id") Long id);

    List<Event> findByStatusAndEventDateAfterOrderByEventDateAsc(EventStatus status, LocalDateTime now);

    List<Event> findByTitleContainingIgnoreCaseAndStatusAndEventDateAfterOrderByEventDateAsc(String title, EventStatus status, LocalDateTime now);

    List<Event> findByCityContainingIgnoreCaseAndStatusAndEventDateAfterOrderByEventDateAsc(String city, EventStatus status, LocalDateTime now);

    List<Event> findByCategoryNameAndStatusAndEventDateAfterOrderByEventDateAsc(String categoryName, EventStatus status, LocalDateTime now);

    List<Event> findByOrganizerEmailIgnoreCaseOrderByUpdatedAtDesc(String organizerEmail);

    Optional<Event> findByGateAccessCode(String gateAccessCode);
    Optional<Event> findByGateAccessCodeIgnoreCase(String gateAccessCode);
}
