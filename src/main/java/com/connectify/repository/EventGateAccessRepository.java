package com.connectify.repository;

import com.connectify.entity.EventGateAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface EventGateAccessRepository extends JpaRepository<EventGateAccess, Long> {
    Optional<EventGateAccess> findByEventIdAndSlot(Long eventId, int slot);
    List<EventGateAccess> findByEventIdOrderBySlotAsc(Long eventId);

    @Query("select g.event.id as eventId, count(g) as total from EventGateAccess g where g.active = true group by g.event.id")
    List<GateCount> countActiveByEvent();
    interface GateCount { Long getEventId(); Long getTotal(); }
}
