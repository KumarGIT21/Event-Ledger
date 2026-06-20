package com.eventledger.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BalanceResponse(
        String accountId,
        BigDecimal balance,
        String currency,
        Instant asOf
) {
}
