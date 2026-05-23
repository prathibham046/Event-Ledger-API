package com.eventledger.api.service;

import com.eventledger.api.dto.BalanceResponse;
import com.eventledger.api.dto.EventRequest;
import com.eventledger.api.dto.EventResponse;
import com.eventledger.api.model.Event;
import com.eventledger.api.repository.EventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EventService {
    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    public ResponseEntity<EventResponse> createEvent(EventRequest request) {
        Optional<Event> existing = repository.findById(request.getEventId());
        if (existing.isPresent()) {
            return ResponseEntity.ok(toResponse(existing.get()));
        }

        Event event = new Event(
                request.getEventId(),
                request.getAccountId(),
                request.getType(),
                request.getAmount(),
                request.getCurrency(),
                request.getEventTimestamp(),
                request.getMetadata()
        );

        try {
            Event saved = repository.saveAndFlush(event);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
        } catch (DataIntegrityViolationException duplicate) {
            Event saved = repository.findById(request.getEventId()).orElseThrow();
            return ResponseEntity.ok(toResponse(saved));
        }
    }

    public EventResponse getEvent(String eventId) {
        Event event = repository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        return toResponse(event);
    }

    public List<EventResponse> listEvents(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<EventResponse> listEvents(String accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId, pageable)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public BalanceResponse getBalance(String accountId) {
        BigDecimal balance = repository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(event -> event.getType() == com.eventledger.api.model.EventType.CREDIT ? event.getAmount() : event.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BalanceResponse(accountId, balance);
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getEventId(),
                event.getAccountId(),
                event.getType(),
                event.getAmount(),
                event.getCurrency(),
                event.getEventTimestamp(),
                event.getMetadata()
        );
    }
}
