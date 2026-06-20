package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.BalanceResponse;
import com.eventledger.gateway.model.EventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ApplyTransactionPayload(
        String eventId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Map<String, Object> metadata
) {
}
