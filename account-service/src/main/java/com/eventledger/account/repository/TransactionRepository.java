package com.eventledger.account.repository;

import com.eventledger.account.model.TransactionRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {

    Optional<TransactionRecord> findByEventId(String eventId);

    List<TransactionRecord> findByAccountIdOrderByEventTimestampAscAppliedAtAsc(String accountId);

    @Query("""
            SELECT COALESCE(SUM(
                CASE WHEN t.type = com.eventledger.account.model.TransactionType.CREDIT
                     THEN t.amount ELSE -t.amount END
            ), 0)
            FROM TransactionRecord t
            WHERE t.accountId = :accountId
            """)
    java.math.BigDecimal computeBalance(@Param("accountId") String accountId);
}
