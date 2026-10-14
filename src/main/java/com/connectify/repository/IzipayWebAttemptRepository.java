package com.connectify.repository;

import com.connectify.entity.IzipayWebAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface IzipayWebAttemptRepository extends JpaRepository<IzipayWebAttempt, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from IzipayWebAttempt a where a.orderNumber = :number")
    Optional<IzipayWebAttempt> findLocked(@Param("number") String number);

    List<IzipayWebAttempt> findByBuyerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(String buyerEmail, String status);
}
