package com.eventledger.api.repository;

import com.eventledger.api.model.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {
    List<Event> findByAccountIdOrderByEventTimestampAsc(String accountId);

    Page<Event> findByAccountIdOrderByEventTimestampAsc(String accountId, Pageable pageable);
}
