# Event Ledger API

A Java Spring Boot service for ingesting financial transaction events with idempotency, out-of-order tolerance, and balance computation.

## Features

- `POST /events` to submit transaction events
- `GET /events/{id}` to fetch a single event
- `GET /events?account={accountId}` to list events for an account in timestamp order with optional `page` and `size`
- `GET /accounts/{accountId}/balance` to compute current account balance
- Embedded H2 in-memory database with idempotent event ingestion
- Automated tests covering validation, duplicate submissions, ordering, and balance logic

## Prerequisites

- Java 17 or newer
- Maven 3.8+

## Setup

```bash
cd /workspaces/Event-Ledger-API
mvn clean package -DskipTests
```

## Run the application

```bash
mvn spring-boot:run
```

The API will be available at `http://127.0.0.1:8080`.

## Run tests

```bash
mvn test
```

## Notes

- The app uses an embedded H2 database in memory for local execution.
- Duplicate event submissions with the same `eventId` return the original event and do not modify balances.
- Simultaneous `POST /events` with the same `eventId` are handled via the database primary-key constraint; one request succeeds and duplicates return the persisted event.
- Event listings are sorted by `eventTimestamp` regardless of arrival order.
