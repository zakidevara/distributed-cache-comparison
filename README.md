# Distributed Cache Comparison

A Spring Boot application comparing four distributed caching strategies with automatic refresh/invalidation:

| Strategy | Type | Refresh Mechanism | Best For |
|----------|------|-------------------|----------|
| **Netflix Hollow** | Immutable dataset | File-based snapshots + announcements | Read-heavy reference data |
| **Kafka KTables** | Streaming state store | Continuous changelog consumption | Event-driven pipelines |
| **Hazelcast Near-Cache** | Distributed + local cache | Automatic invalidation events | Ultra-low latency lookups |
| **Redis Client-Side** | Key-value + local cache | TRACKING-based invalidation | Simple caching, broad ecosystem |

## Quick Start

```powershell
# Choose a caching strategy and run:

# Netflix Hollow (filesystem-based)
./gradlew bootRun --args='--spring.profiles.active=filesystem'

# Kafka KTables (requires Kafka)
docker-compose -f docker-compose-kafka.yml up -d
./gradlew bootRun --args='--spring.profiles.active=kafka'

# Hazelcast Near-Cache (embedded)
./gradlew bootRun --args='--spring.profiles.active=hazelcast'

# Redis Client-Side (requires Redis)
docker-compose -f docker-compose-redis.yml up -d
./gradlew bootRun --args='--spring.profiles.active=redis'
```

## Project Structure

```
src/main/java/com/devara/hollow/
├── HollowApplication.java           # Spring Boot main application
├── HollowController.java            # Hollow REST API
├── KafkaController.java             # Kafka REST API
├── HazelcastController.java         # Hazelcast REST API
├── RedisController.java             # Redis REST API
├── config/
│   ├── HollowUIConfiguration.java   # Hollow config
│   ├── KafkaConfig.java             # Kafka Streams config
│   ├── HazelcastConfig.java         # Hazelcast Near-Cache config
│   └── RedisConfig.java             # Redis TRACKING config
├── service/
│   ├── HollowProducerService.java   # Hollow producer
│   ├── HollowConsumerService.java   # Hollow consumer
│   ├── kafka/                       # Kafka producer/consumer
│   ├── hazelcast/                   # Hazelcast producer/consumer
│   └── redis/                       # Redis producer/consumer
└── model/
    └── UserAccount.java             # Sample data model
```

## Documentation

| Document | Description |
|----------|-------------|
| [COMPARISON-README.md](COMPARISON-README.md) | Detailed comparison of all four approaches |
| [BENCHMARK-README.md](BENCHMARK-README.md) | JMH benchmarking guide |
| [KAFKA-README.md](KAFKA-README.md) | Kafka KTable implementation details |

## Architecture Overview

### 1. Netflix Hollow
```
Producer → Snapshot/Delta Files → Blob Storage → Announcement → Consumer Refresh
```
- Full dataset published each cycle
- Delta updates for efficiency
- Time travel capability

### 2. Kafka KTables
```
Producer → Kafka Topic (compacted) → Kafka Streams → KTable (RocksDB) → Query
```
- Real-time streaming updates
- Automatic state materialization
- Partition-based scaling

### 3. Hazelcast Near-Cache
```
Producer → Distributed IMap → Invalidation Event → Near-Cache Clear → Fresh Fetch
```
- Sub-microsecond local reads
- Automatic invalidation on updates
- No manual refresh needed

### 4. Redis Client-Side Caching
```
Producer → Redis SET → TRACKING Invalidation → Local Cache Clear → Fresh Fetch
```
- RESP3 protocol with TRACKING
- ConcurrentHashMap local cache
- Broad ecosystem compatibility

## REST API Endpoints

Each caching strategy exposes similar endpoints:

| Endpoint Pattern | Method | Description |
|------------------|--------|-------------|
| `/api/{strategy}/produce` | POST | Write data |
| `/api/{strategy}/produce/sample` | POST | Generate sample data |
| `/api/{strategy}/consume/{id}` | GET | Read by ID |
| `/api/{strategy}/consume/all` | GET | Read all records |
| `/api/{strategy}/consume/stats` | GET | Cache statistics |

Where `{strategy}` is one of: `hollow`, `kafka`, `hazelcast`, `redis`

## Benchmarks

Run JMH benchmarks to compare performance:

```powershell
# Start required services
docker-compose -f docker-compose-kafka.yml up -d
docker-compose -f docker-compose-redis.yml up -d

# Run all benchmarks
./gradlew jmh

# Run refresh latency comparison
./gradlew jmh -Pjmh.includes='RefreshLatencyBenchmark'
```

### Expected Results

| Metric | Hollow | Kafka | Hazelcast | Redis |
|--------|--------|-------|-----------|-------|
| **Read Latency** | ~100ns | ~1ms | ~150ns | ~80ns |
| **Refresh Latency** | ~1-5ms | ~50-100ms | ~0.1-1ms | ~0.1-0.5ms |
| **Write Latency** | ~15ms (batch) | ~5ms | ~1ms | ~0.5ms |

## Demo Scripts

Interactive PowerShell demos for each strategy:

```powershell
./demo-auto-refresh.ps1           # Hollow
./demo-kafka-auto-refresh.ps1     # Kafka
./demo-hazelcast-auto-refresh.ps1 # Hazelcast
./demo-redis-auto-refresh.ps1     # Redis
```

## Prerequisites

- **Java 21+**
- **Gradle** (wrapper included)
- **Docker** (for Kafka and Redis)

## Configuration Profiles

| Profile | Config File | External Dependencies |
|---------|-------------|----------------------|
| `filesystem` | `application-filesystem.properties` | None |
| `s3` | `application-s3.properties` | AWS S3 |
| `kafka` | `application-kafka.properties` | Kafka broker |
| `hazelcast` | `application-hazelcast.properties` | None (embedded) |
| `redis` | `application-redis.properties` | Redis server |

## Data Model

```java
@HollowPrimaryKey(fields="id")
public class UserAccount {
    private long id;
    private String username;
    private boolean active;
}
```

## When to Use Each Strategy

| Use Case | Recommended |
|----------|-------------|
| Large read-heavy reference data | **Hollow** |
| Real-time event streaming | **Kafka KTables** |
| Ultra-low latency requirements | **Hazelcast** |
| Simple caching, existing Redis | **Redis** |
| Time travel / versioning needed | **Hollow** |
| Already have Kafka infrastructure | **Kafka KTables** |

## Technologies

- **Spring Boot 4.0** - Application framework
- **Netflix Hollow 7.14** - Immutable dataset library
- **Apache Kafka 3.7** - Streaming platform
- **Hazelcast 5.4** - In-memory data grid
- **Redis 7.2** - Key-value store with TRACKING
- **Lettuce 6.3** - Redis client with RESP3
- **JMH 0.7** - Microbenchmarking

## License

This project is for demonstration and comparison purposes.

## Resources

- [Netflix Hollow](https://hollow.how)
- [Apache Kafka Streams](https://kafka.apache.org/documentation/streams/)
- [Hazelcast Near-Cache](https://docs.hazelcast.com/hazelcast/latest/performance/near-cache)
- [Redis Client-Side Caching](https://redis.io/docs/manual/client-side-caching/)
