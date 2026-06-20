package com.eventledger.gateway.repository;

import com.eventledger.gateway.model.EventRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<EventRecord, String> {

    List<EventRecord> findByAccountIdOrderByEventTimestampAscCreatedAtAsc(String accountId);
}
