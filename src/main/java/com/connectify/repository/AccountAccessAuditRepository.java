package com.connectify.repository;

import com.connectify.entity.AccountAccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountAccessAuditRepository extends JpaRepository<AccountAccessAudit, Long> {
    List<AccountAccessAudit> findTop30ByOrderByCreatedAtDesc();
}
