# Event Ledger

A two-service Event Ledger system built with **Spring Boot** and **Java**. The **Event Gateway** is the public entry point for transaction events; the **Account Service** is an internal service that maintains account balances and transaction history.

## Architecture

```
Browser / Client ──→ Event Gateway API (port 8080, H2)
                         │ REST (sync)
                         ▼
                   Account Service (port 8081, MySQL)
```

| Service | Database | Responsibility |
|---------|----------|----------------|
| **Event Gateway** | H2 (in-memory) | Validates events, enforces idempotency, stores event records, forwards transactions to Account Service |
| **Account Service** | MySQL | Applies transactions, computes balances, stores transaction history |

### Key behaviors

- **Idempotency**: Duplicate `eventId` submissions return the original event (`200 OK`) without changing the balance.
- **Out-of-order events**: Events may arrive in any order; balances are computed from all transactions regardless of arrival order. Event listings are sorted by `eventTimestamp`.
- **Graceful degradation**: When Account Service is unavailable, `POST /events` and balance queries return `503 Service Unavailable`. `GET /events` endpoints continue to work from Gateway-local data.

### Distributed tracing

Each Gateway request generates an `X-Trace-Id` header. The trace ID is propagated to Account Service on downstream HTTP calls and included in structured JSON logs on both services.

### Resiliency pattern

The Gateway uses **Resilience4j** with a combined approach:

1. **Circuit breaker** — stops calling Account Service after repeated failures (50% failure rate over a sliding window), returning fast errors instead of hanging.
2. **Retry with exponential backoff** — retries transient network/server errors (up to 3 attempts) before counting as a failure.
3. **HTTP timeouts** — RestTemplate connect (2s) and read (3s) timeouts prevent indefinite blocking.

**Why this combination?** Retries handle transient blips; timeouts bound latency; the circuit breaker protects the Gateway from cascading overload when Account Service is persistently down. This directly supports the graceful degradation requirement.

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose (optional, recommended)

## Setup

Clone the repository and build all modules:

```bash
./mvnw clean package
```

Or, if Maven is installed globally:

```bash
mvn clean package
```

## Running with Docker Compose (recommended)

```bash
docker compose up --build
```

Services:

| Service | URL |
|---------|-----|
| Event Gateway | http://localhost:8080 |
| Account Service | http://localhost:8081 |
| MySQL | localhost:3306 |

### Example requests

Submit an event:

```bash
curl -s -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": {"source": "mainframe-batch"}
  }'
```

Get account balance (via Gateway):

```bash
curl -s http://localhost:8080/accounts/acct-123/balance
```

List events for an account:

```bash
curl -s "http://localhost:8080/events?account=acct-123"
```

Health checks:

```bash
curl -s http://localhost:8080/health
curl -s http://localhost:8081/health
```

## Running locally (without Docker)

### 1. Start MySQL

Ensure MySQL is running with database `event_ledger` and credentials matching `account-service/src/main/resources/application.yml` (default: user `eventledger`, password `eventledger`).

### 2. Start Account Service

```bash
./mvnw -pl account-service spring-boot:run
```

### 3. Start Event Gateway

```bash
./mvnw -pl event-gateway spring-boot:run
```

## Running tests

```bash
./mvnw test
```

Test coverage includes:

- **Core functionality**: idempotency, out-of-order events, balance computation, validation
- **Resiliency**: Account Service failure simulation, circuit breaker behavior, `503` responses
- **Trace propagation**: `X-Trace-Id` header flow from Gateway to Account Service
- **Integration**: End-to-end Gateway → Account Service flow

## API Reference

### Event Gateway (public)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/events` | Submit a transaction event |
| `GET` | `/events/{id}` | Retrieve a single event |
| `GET` | `/events?account={accountId}` | List events for an account (by `eventTimestamp`) |
| `GET` | `/accounts/{accountId}/balance` | Get account balance (proxied to Account Service) |
| `GET` | `/health` | Health check with DB status and metrics |

### Account Service (internal)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/accounts/{accountId}/transactions` | Apply a transaction |
| `GET` | `/accounts/{accountId}/balance` | Get current balance |
| `GET` | `/accounts/{accountId}` | Account details and transaction history |
| `GET` | `/health` | Health check with DB status and metrics |

## Observability

Both services emit **JSON structured logs** (via Logstash encoder) including `traceId`, `service`, timestamp, and log level.

Custom metrics (request count, error count, average latency per endpoint) are exposed in the `/health` response under the `metrics` key.
