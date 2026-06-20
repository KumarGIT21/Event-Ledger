package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.client.ApplyTransactionPayload;
import com.eventledger.gateway.dto.BalanceResponse;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.dto.SubmitEventRequest;
import com.eventledger.gateway.model.EventRecord;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;

    public EventService(
            EventRepository eventRepository,
            AccountServiceClient accountServiceClient,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EventResponse submitEvent(SubmitEventRequest request) {
        var existing = eventRepository.findById(request.eventId());
        if (existing.isPresent()) {
            log.info("Duplicate event submission eventId={}", request.eventId());
            return toResponse(existing.get(), true);
        }

        ApplyTransactionPayload payload = new ApplyTransactionPayload(
                request.eventId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                request.metadata()
        );

        try {
            accountServiceClient.applyTransaction(request.accountId(), payload);
        } catch (RuntimeException ex) {
            log.error("Failed to apply transaction for eventId={}: {}", request.eventId(), ex.getMessage());
            throw ex;
        }

        String metadataJson = serializeMetadata(request.metadata());
        EventRecord record = new EventRecord(
                request.eventId(),
                request.accountId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                metadataJson
        );

        try {
            eventRepository.save(record);
        } catch (DataIntegrityViolationException ex) {
            EventRecord concurrent = eventRepository.findById(request.eventId())
                    .orElseThrow(() -> ex);
            return toResponse(concurrent, true);
        }

        log.info("Event stored eventId={} accountId={}", request.eventId(), request.accountId());
        return toResponse(record, false);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String eventId) {
        EventRecord record = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return toResponse(record, false);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAscCreatedAtAsc(accountId)
                .stream()
                .map(record -> toResponse(record, false))
                .toList();
    }

    public BalanceResponse getBalance(String accountId) {
        return accountServiceClient.getBalance(accountId);
    }

    private EventResponse toResponse(EventRecord record, boolean duplicate) {
        return new EventResponse(
                record.getEventId(),
                record.getAccountId(),
                record.getType(),
                record.getAmount(),
                record.getCurrency(),
                record.getEventTimestamp(),
                deserializeMetadata(record.getMetadata()),
                record.getStatus(),
                record.getCreatedAt(),
                duplicate
        );
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid metadata", ex);
        }
    }

    private Map<String, Object> deserializeMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }
}
