# Candle Service

A production-grade real-time market data aggregation service that ingests
bid/ask events via Kafka and aggregates them into OHLC candlestick format
for multiple symbols and timeframes.

---

## Architecture

```
BidAskSimulator (@Scheduled)
        ↓
BidAskProducer → Kafka (market.bidask, 6 partitions)
        ↓
BidAskConsumer (3 consumer threads, manual ack)
        ↓
AggregationDispatcher → CandleBuilder per (symbol, interval)
        ↓
StaleCandleFlusher (@Scheduled, closes expired buckets)
        ↓
CandleStore (InMemory or TimescaleDB)
        ↓
GET /history REST API
```

---

## Tech Stack

| Component       | Technology                         |
|-----------------|------------------------------------|
| Framework       | Spring Boot 3.2                    |
| Language        | Java 21 (virtual threads enabled)  |
| Message Broker  | Apache Kafka (KRaft, no ZooKeeper) |
| Database        | TimescaleDB (PostgreSQL hypertable)|
| Security        | API Key auth + Bucket4j rate limit |
| API Docs        | SpringDoc OpenAPI / Swagger UI     |
| Observability   | Micrometer + Prometheus            |
| Testing         | JUnit 5, Mockito, EmbeddedKafka    |

---

## Features

- Real-time bid/ask ingestion via Kafka
- OHLC aggregation for 4 symbols x 5 intervals (20 builders)
- Supported intervals: 1s, 5s, 1m, 15m, 1h
- Supported symbols: BTC-USD, ETH-USD, SOL-USD, BNB-USD
- Stale candle flusher — closes open candles on timeout
- TradingView Lightweight Charts compatible API response
- API key authentication with rate limiting (100 req/sec per key)
- Swappable storage — in-memory (default) or TimescaleDB
- Prometheus metrics at /actuator/prometheus
- Swagger UI at /swagger-ui.html
- File logging with rolling policy (7 days history)
- Health check endpoint at /actuator/health

---

## Assumptions and Trade-offs

### Price Calculation
Mid-price `(bid + ask) / 2` is used as the OHLC price. In a production
system you would maintain separate bid and ask candles. This was a
deliberate simplification noted upfront.

### Volume
Volume is synthetic — it counts the number of ticks per candle rather
than actual traded volume. Real volume would require trade data not
just quotes.

### API Key Auth vs JWT
API key authentication was chosen over JWT/OAuth2 because this is a
machine-to-machine data API, not a user-facing auth flow. Keys are
loaded from environment variables and never committed to source control.
JWT would be appropriate if user-level delegated access were required.

### Kafka vs In-Process Queue
Kafka is used even with a simulated source to demonstrate the full
production pipeline. The ingestion layer is abstracted behind the
producer/consumer pattern so swapping to a WebSocket feed requires
only a new producer implementation with no downstream changes.

### Single Broker
Kafka runs as a single broker (replication-factor=1) for local
development. In production you would run 3+ brokers with
replication-factor=3 and min.insync.replicas=2.

### In-Memory vs TimescaleDB
In-memory store is the default profile — no external dependencies
for development and testing. TimescaleDB is activated via the
timescale Spring profile for production use.

### Raw Event Storage
Raw bid/ask events are not persisted to the database. Kafka serves
as the event store with 24-hour retention configured on the
`market.bidask` topic. Events replay automatically on restart via
`auto-offset-reset: earliest` and manual offset commit — making
a separate database event table redundant.

### Candle Calculation Decoupling
The `CandleBuilder` has zero knowledge of the data source. It only
receives a `BidAskEvent` and produces a `Candle`. The ingestion layer
(Kafka, simulator, or WebSocket) is completely interchangeable without
touching the aggregation logic.

---

## Quick Start

### Prerequisites
- Docker Desktop

### Start full stack

```bash
docker compose up -d
```

This starts:
- Kafka (KRaft mode, port 9092)
- kafka-init (creates topic, exits after)
- TimescaleDB (port 5432)
- candle-service (port 8080, timescale profile active)

### Wait 30 seconds then verify

```bash
docker compose ps
```

All containers should show as running.
kafka-init will show exited 0 — this is correct.

### Check logs

```bash
docker compose logs app --follow
```

---

### Running Locally (without Docker)

Start only infrastructure:
```bash
docker compose up kafka timescaledb -d
```

Then run from IntelliJ:
```
Right click CandleServiceApplication.java
  → Run 'CandleServiceApplication'
```

Logs will be written to `logs/candle-service.log`

---

## API Usage

### Swagger UI

Open in browser:
```
http://localhost:8080/swagger-ui.html
```

Click Authorize and enter API key:
```
dev-key-abc123
```

### History Endpoint

```
GET /history?symbol=BTC-USD&interval=1m&from=1620000000&to=1620003600
```

Required header:
```
X-API-Key: dev-key-abc123
```

Example response:
```json
{
  "s": "ok",
  "t": [1620000000, 1620000060],
  "o": [29500.5, 29501.0],
  "h": [29510.0, 29505.0],
  "l": [29490.0, 29500.0],
  "c": [29505.0, 29502.0],
  "v": [10, 8]
}
```

No data response:
```json
{
  "s": "no_data",
  "t": [], "o": [], "h": [], "l": [], "c": [], "v": []
}
```

Error response:
```json
{
  "s": "error: Unknown interval: 99x"
}
```

### Supported Symbols
```
BTC-USD
ETH-USD
SOL-USD
BNB-USD
```

### Supported Intervals
```
1s   5s   1m   15m   1h
```

### Health Check
```
GET http://localhost:8080/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

### Prometheus Metrics
```
GET http://localhost:8080/actuator/prometheus
```

### Kafka UI

Visual interface to inspect Kafka messages and consumer lag:
http://localhost:8090

Shows:
- Live BidAskEvents flowing through `market.bidask` topic
- All 6 partitions and message counts
- Consumer group `candle-aggregator` and lag per partition
---

## Running Tests

Tests use EmbeddedKafka and H2 in-memory database.
No Docker required.

### Run all tests in IntelliJ
```
Right click src/test/java
  → Run All Tests
```

### Test coverage

| Test Class                   | Tests | What it covers             |
|------------------------------|-------|----------------------------|
| CandleBuilderTest            | 10    | OHLC aggregation logic     |
| InMemoryCandleStoreTest      | 5     | Storage and range queries  |
| HistoryControllerTest        | 6     | REST API and security      |
| CandleServiceIntegrationTest | 5     | Full pipeline end to end   |
| Total                        | 26    |                            |

### What the tests verify

**CandleBuilderTest** covers:
- First event opens candle without closing it
- Events in same bucket update OHLC correctly
- New bucket event closes current candle
- Candle time is bucket start in UNIX seconds
- Late events are ignored
- Single event candle has equal OHLC values
- Volume counts all ticks in bucket
- Force flush returns candle when bucket expired
- High is always the maximum mid price
- Low is always the minimum mid price

**CandleServiceIntegrationTest** covers:
- Full pipeline: publish to Kafka → aggregate → store → query
- REST API returns correct candle data
- Multiple symbols aggregated independently
- 401 returned when API key missing
- no_data returned for symbol with no events

---

## Storage Profiles

### Default — In-Memory (no DB needed)
```bash
java -jar candle-service.jar
```

### TimescaleDB
```bash
java -jar candle-service.jar --spring.profiles.active=timescale
```

---

## Security

| Route                | Auth Required |
|----------------------|---------------|
| GET /history         | Yes           |
| GET /actuator/health | No            |
| GET /swagger-ui/**   | No            |
| GET /v3/api-docs/**  | No            |

Rate limit: 100 requests per second per API key.
Exceeding limit returns HTTP 429.

---

## Observability

### Logging
- Console logging — always active, visible via `docker compose logs app`
- File logging — written to `logs/candle-service.log`
- Rolling policy — new file per day, 7 days history retained
- Log levels — DEBUG for `com.candleservice`, WARN for Kafka internals

### What gets logged
- Every bid/ask event received from Kafka (DEBUG)
- Every candle closed by CandleBuilder (DEBUG)
- Every candle saved to store (INFO)
- Every stale candle flushed (INFO)
- Every API request received (INFO)
- Rate limit violations (WARN)
- Processing failures (ERROR)

### Metrics
All metrics exposed at `/actuator/prometheus` and compatible with Grafana:
- JVM memory, GC, threads
- HTTP request rates and latencies
- Kafka consumer lag
- Custom candle creation counters

---

## Extensibility

### Adding a new interval
One line in `Interval.java` enum:

    H4("4h", 14_400_000L),  // add this

One line in `application.yml`:

    candle:
      intervals:
        - 1s
        - 5s
        - 1m
        - 15m
        - 1h
        - 4h   # add this

No other changes needed.

### Adding a new symbol
One line in `application.yml`:
```yaml
candle:
  symbols:
    - BTC-USD
    - ETH-USD
    - SOL-USD
    - BNB-USD
    - XRP-USD   # add this
```

No other changes needed.

### Swapping the data source
Implement `MarketEventSource` and publish to Kafka:
```java
// Example: WebSocket feed replacing simulator
@Component
@Profile("live")
public class BinanceWebSocketSource {
    // connects to wss://stream.binance.com
    // calls producer.publish(event) for each tick
    // CandleBuilder is completely unaffected
}
```

---

## Environment Variables

| Variable                        | Default                                    | Description             |
|---------------------------------|--------------------------------------------|-------------------------|
| SPRING_KAFKA_BOOTSTRAP_SERVERS  | localhost:9092                             | Kafka broker address    |
| SPRING_DATASOURCE_URL           | jdbc:postgresql://localhost:5432/candledb  | DB URL                  |
| SPRING_DATASOURCE_USERNAME      | candle                                     | DB username             |
| SPRING_DATASOURCE_PASSWORD      | candle_secret                              | DB password             |
| SPRING_PROFILES_ACTIVE          | (empty = in-memory)                        | timescale for DB        |
| API_KEY_1                       | dev-key-abc123                             | Primary API key         |
| API_KEY_2                       | (empty)                                    | Secondary API key       |
| LOG_PATH                        | logs                                       | Log file directory      |
| CANDLE_SYMBOLS                  | BTC-USD,ETH-USD,SOL-USD,BNB-USD           | Symbols to simulate     |
| CANDLE_INTERVALS                | 1s,5s,1m,15m,1h                           | Intervals to aggregate  |
| CANDLE_SIMULATOR_ENABLED        | true                                       | Enable simulator        |
| CANDLE_SIMULATOR_RATE_MS        | 500                                        | Simulator fire rate     |
| CANDLE_FLUSHER_RATE_MS          | 1000                                       | Stale candle flush rate |

---

## Project Structure

```
src/main/java/com/candleservice/
├── CandleServiceApplication.java
├── aggregation/
│   ├── AggregationDispatcher.java
│   ├── CandleBuilder.java
│   └── StaleCandleFlusher.java
├── api/
│   ├── HistoryController.java
│   └── dto/
│       └── HistoryResponse.java
├── config/
│   ├── KafkaConsumerConfig.java
│   ├── KafkaTopicConfig.java
│   └── OpenApiConfig.java
├── domain/
│   ├── BidAskEvent.java
│   ├── Candle.java
│   └── Interval.java
├── ingestion/
│   ├── kafka/
│   │   ├── BidAskConsumer.java
│   │   └── BidAskProducer.java
│   └── simulator/
│       └── BidAskSimulator.java
├── security/
│   ├── ApiKeyAuthFilter.java
│   ├── RateLimiterService.java
│   └── SecurityConfig.java
└── storage/
    ├── CandleStore.java
    ├── inmemory/
    │   └── InMemoryCandleStore.java
    └── timescale/
        └── TimescaleCandleStore.java

src/main/resources/
├── application.yml
├── logback-spring.xml
└── db/
    └── migration/
        └── V1__init.sql

src/test/java/com/candleservice/
├── aggregation/
│   ├── CandleBuilderTest.java
│   └── InMemoryCandleStoreTest.java
├── api/
│   └── HistoryControllerTest.java
└── integration/
    └── CandleServiceIntegrationTest.java
```

---

## Bonus Features Implemented

- Virtual threads via Spring Boot 3 (`spring.threads.virtual.enabled=true`)
- KRaft Kafka (no ZooKeeper dependency)
- TimescaleDB hypertable with compression configured
- Stale candle flusher handles gaps in event streams
- Rate limiting per API key via Bucket4j token bucket
- Multi-stage Docker build (small final image)
- UPSERT on candle save (safe for replays)
- Manual Kafka offset commit (replay on crash)
- Partitioned by symbol key (ordering guaranteed per symbol)
- File logging with rolling policy via logback-spring.xml
- Structured console logging with thread and level context
- Extensibility section documenting how to add intervals, symbols and sources