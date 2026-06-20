package com.eventledger.account.dto;

import com.eventledger.account.model.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AccountDetailsResponse(
        String accountId,
        BigDecimal balance,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        List<TransactionSummary> recentTransactions
) {
    public record TransactionSummary(
            String eventId,
            TransactionType type,
            BigDecimal amount,
            String currency,
            Instant eventTimestamp,
            Instant appliedAt
    ) {
    }
}
