# Benchmarking Guide

This document explains how to run JMH benchmarks comparing the four caching implementations: **Netflix Hollow**, **Kafka KTables**, **Hazelcast Near-Cache**, and **Redis Client-Side Caching**.

## Overview

The project includes JMH (Java Microbenchmark Harness) benchmarks to measure and compare:

| Metric | Description |
|--------|-------------|
| **Write Latency** | Time to publish/write records |
| **Read Latency** | Time to query a single record |
| **Bulk Read Latency** | Time to scan all records |
| **Refresh Latency** | End-to-end time from publish to consumer visibility |

## Prerequisites

### For All Benchmarks
- Java 21+
- Gradle (wrapper included)

### For Kafka Benchmarks
- Running Kafka broker at `localhost:9092`
- Start with: `docker-compose -f docker-compose-kafka.yml up -d`

### For Redis Benchmarks
- Running Redis 7.0+ at `localhost:6379`
- Start with: `docker-compose -f docker-compose-redis.yml up -d`

### For Hazelcast Benchmarks
- No external dependencies (embedded mode)

### For Hollow Benchmarks
- No external dependencies (uses filesystem)

## Running Benchmarks

### Run All Benchmarks

```powershell
./gradlew jmh
```

This runs all benchmark classes and generates a JSON report.

### Run Specific Benchmark Class

```powershell
# Hollow only
./gradlew jmh -Pjmh.includes='HollowBenchmark'

# Kafka only (requires running Kafka)
./gradlew jmh -Pjmh.includes='KafkaBenchmark'

# Hazelcast only
./gradlew jmh -Pjmh.includes='HazelcastBenchmark'

# Redis only (requires running Redis)
./gradlew jmh -Pjmh.includes='RedisBenchmark'

# Refresh latency comparison (all four)
./gradlew jmh -Pjmh.includes='RefreshLatencyBenchmark'
```

### Run Specific Benchmark Method

```powershell
# Only read latency benchmarks
./gradlew jmh -Pjmh.includes='.*ReadLatency.*'

# Only write latency benchmarks
./gradlew jmh -Pjmh.includes='.*writeLatency.*'
```

### Customize Iterations

```powershell
# Quick run (fewer iterations)
./gradlew jmh -Pjmh.warmupIterations=1 -Pjmh.iterations=3

# Thorough run (more iterations)
./gradlew jmh -Pjmh.warmupIterations=5 -Pjmh.iterations=10 -Pjmh.fork=2
```

## Benchmark Classes

### 1. HollowBenchmark

**Location:** `src/jmh/java/com/devara/hollow/benchmark/HollowBenchmark.java`

Measures Netflix Hollow filesystem-based operations:

| Method | Description |
|--------|-------------|
| `writeLatency` | Time to run a publish cycle with 10K records |
| `singleReadLatency` | Time to access a single record by ordinal |
| `bulkReadLatency` | Time to iterate all records |
| `refreshLatency` | Time to trigger consumer refresh |

### 2. KafkaBenchmark

**Location:** `src/jmh/java/com/devara/hollow/benchmark/KafkaBenchmark.java`

Measures Kafka KTable operations:

| Method | Description |
|--------|-------------|
| `writeLatency` | Time to publish 10K records to Kafka topic |
| `singleReadLatency` | Time to query KTable state store |
| `bulkReadLatency` | Time to iterate all entries in state store |

**Note:** Requires running Kafka broker!

### 3. HazelcastBenchmark

**Location:** `src/jmh/java/com/devara/hollow/benchmark/HazelcastBenchmark.java`

Measures Hazelcast Near-Cache operations:

| Method | Description |
|--------|-------------|
| `writeLatency` | Time to putAll 10K records |
| `singleReadLatency_NearCacheHit` | Time to read from warm Near-Cache |
| `singleReadLatency_Random` | Time to read random keys |
| `bulkReadLatency` | Time to iterate all values |
| `singleWriteLatency` | Time to put single record (with invalidation) |
| `containsKeyLatency` | Time to check key existence |

### 4. RedisBenchmark

**Location:** `src/jmh/java/com/devara/hollow/RedisBenchmark.java`

Measures Redis client-side caching operations:

| Method | Description |
|--------|-------------|
| `localCacheHit` | Time to read from local ConcurrentHashMap |
| `cacheMissFetch` | Time to read from Redis on cache miss |
| `redisGet` | Time for Redis GET (network round-trip) |
| `redisSet` | Time for Redis SET (write operation) |
| `cacheFrontendGet` | Time using CacheFrontend (automatic cache-aside) |
| `redisMget` | Time for bulk MGET (100 keys) |
| `redisMset` | Time for bulk MSET (100 keys) |
| `redisPipelineGet` | Time for pipelined GET operations |

**Note:** Requires running Redis 7.0+!

### 5. RefreshLatencyBenchmark

**Location:** `src/jmh/java/com/devara/hollow/benchmark/RefreshLatencyBenchmark.java`

**The key comparison benchmark!** Measures end-to-end refresh latency for all four approaches:

| Method | Description |
|--------|-------------|
| `hollowRefreshLatency` | Hollow: publish cycle + triggerRefresh |
| `kafkaRefreshLatency` | Kafka: send to topic + wait for KTable visibility |
| `hazelcastRefreshLatency` | Hazelcast: put + read from another instance |
| `redisRefreshLatency` | Redis: SET + TRACKING invalidation + read |

## Output Location

Results are written to:

```
build/reports/jmh/results.json
```

## Understanding Results

### Sample Output

```
Benchmark                                          Mode  Cnt    Score    Error  Units
HollowBenchmark.writeLatency                       avgt    5   15.234 ±  1.123  ms/op
HollowBenchmark.singleReadLatency                  avgt    5    0.089 ±  0.012  us/op
HazelcastBenchmark.singleReadLatency_NearCacheHit  avgt    5    0.156 ±  0.023  us/op
KafkaBenchmark.singleReadLatency                   avgt    5  892.456 ± 45.678  us/op
RefreshLatencyBenchmark.hollowRefreshLatency       avgt    5   12.345 ±  0.987  ms/op
RefreshLatencyBenchmark.kafkaRefreshLatency        avgt    5   48.234 ±  3.456  ms/op
RefreshLatencyBenchmark.hazelcastRefreshLatency    avgt    5    0.234 ±  0.045  ms/op
RefreshLatencyBenchmark.redisRefreshLatency        avgt    5    0.312 ±  0.056  ms/op
```

### Key Metrics

| Column | Meaning |
|--------|---------|
| `Mode` | Benchmark mode (avgt = average time) |
| `Cnt` | Number of measurement iterations |
| `Score` | Average result |
| `Error` | Margin of error (99.9% confidence) |
| `Units` | Time unit (us = microseconds, ms = milliseconds)

### Expected Performance Characteristics

| Implementation | Read Latency | Write Latency | Refresh Latency | Best For |
|----------------|--------------|---------------|-----------------|----------|
| **Hollow** | ~100ns | ~10-20ms | ~1-5ms | Read-heavy, large datasets, time-travel |
| **Kafka KTable** | ~1ms | ~5ms | ~50-100ms | Event streaming, distributed consumers |
| **Hazelcast Near-Cache** | ~100-200ns | ~1ms | ~0.1-1ms | Ultra-low latency, auto-invalidation |
| **Redis Client-Side** | ~80-150ns | ~0.5ms | ~0.1-0.5ms | Simple caching, broad ecosystem |

## Generating Reports

### JSON Report (Default)

```powershell
./gradlew jmh
# Output: build/reports/jmh/results.json
```

### Text Report

```powershell
./gradlew jmh -Pjmh.resultFormat=TEXT
# Output: build/reports/jmh/results.txt
```

### CSV Report

```powershell
./gradlew jmh -Pjmh.resultFormat=CSV
# Output: build/reports/jmh/results.csv
```

### Visualize with JMH Visualizer

1. Run benchmarks to generate `results.json`
2. Go to [JMH Visualizer](https://jmh.morethan.io/)
3. Upload `build/reports/jmh/results.json`
4. View interactive charts comparing all benchmarks

## Quick Start

### Compare All Four Implementations

```powershell
# 1. Start Kafka (required for Kafka benchmarks)
docker-compose -f docker-compose-kafka.yml up -d

# 2. Start Redis (required for Redis benchmarks)
docker-compose -f docker-compose-redis.yml up -d

# 3. Wait for services to be ready
Start-Sleep -Seconds 15

# 4. Run the refresh latency comparison
./gradlew jmh -Pjmh.includes='RefreshLatencyBenchmark'

# 5. View results
Get-Content build/reports/jmh/results.json | ConvertFrom-Json | Format-Table
```

### Run Quick Smoke Test

```powershell
# Fast run with minimal iterations (for testing)
./gradlew jmh -Pjmh.includes='HazelcastBenchmark' -Pjmh.warmupIterations=1 -Pjmh.iterations=1
```

## Troubleshooting

### "Connection refused" for Kafka Benchmarks

Kafka is not running. Start it with:
```powershell
docker-compose -f docker-compose-kafka.yml up -d
```

### "Connection refused" for Redis Benchmarks

Redis is not running. Start it with:
```powershell
docker-compose -f docker-compose-redis.yml up -d
```

### "TRACKING not available" Warning

Redis version is too old. Ensure Redis 7.0+ is running:
```powershell
docker exec hollow-redis redis-cli INFO server | Select-String "redis_version"
```

### Slow Hazelcast Startup

First run takes longer due to cluster formation. Subsequent runs are faster.

### OutOfMemoryError

Increase JMH heap size:
```powershell
./gradlew jmh -Pjmh.jvmArgs='-Xmx4g'
```

### Results Vary Too Much

Increase iterations for more stable results:
```powershell
./gradlew jmh -Pjmh.warmupIterations=5 -Pjmh.iterations=10 -Pjmh.fork=3
```

## Configuration Reference

JMH configuration is in `build.gradle`:

```groovy
jmh {
    iterations = 5           // Measurement iterations
    warmupIterations = 3     // Warmup iterations
    fork = 1                 // Number of JVM forks
    resultFormat = 'JSON'    // Output format
    resultsFile = project.file("${project.buildDir}/reports/jmh/results.json")
}
```

Override any setting via command line with `-Pjmh.<setting>=<value>`.
