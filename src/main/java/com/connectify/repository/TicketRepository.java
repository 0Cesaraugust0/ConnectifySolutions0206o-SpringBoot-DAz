package com.connectify.repository;

import com.connectify.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.code = :code")
    Optional<Ticket> findLockedByCode(@Param("code") String code);

    List<Ticket> findByPurchaseId(Long purchaseId);

    List<Ticket> findByAttendeeEmailOrderByGeneratedAtDesc(String attendeeEmail);
}
