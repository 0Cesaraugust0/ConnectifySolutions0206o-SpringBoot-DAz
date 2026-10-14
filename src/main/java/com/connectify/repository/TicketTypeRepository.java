package com.connectify.repository;

import com.connectify.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.id = :id")
    Optional<TicketType> findLockedById(@Param("id") Long id);

    List<TicketType> findByEventIdOrderByPriceAsc(Long eventId);

    List<TicketType> findByEventIdAndActiveTrueOrderByPriceAsc(Long eventId);
}
