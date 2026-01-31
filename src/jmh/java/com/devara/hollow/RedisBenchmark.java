package com.devara.hollow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TrackingArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.support.caching.CacheAccessor;
import io.lettuce.core.support.caching.CacheFrontend;
import io.lettuce.core.support.caching.ClientSideCaching;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for Redis client-side caching operations.
 * 
 * Measures:
 * - Local cache hit latency (ConcurrentHashMap lookup)
 * - Cache miss + Redis fetch latency
 * - Write latency with invalidation
 * - Bulk operations
 * 
 * Prerequisites:
 * - Redis 7.0+ running on localhost:6379
 * - Run: docker-compose -f docker-compose-redis.yml up -d
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class RedisBenchmark {

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private CacheFrontend<String, String> cacheFrontend;
    private Map<String, String> localCache;
    private ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "bench:user:";
    private static final int NUM_RECORDS = 10000;

    @Setup(Level.Trial)
    public void setup() {
        objectMapper = new ObjectMapper();
        localCache = new ConcurrentHashMap<>();

        // Create Redis client with RESP3 for TRACKING support
        RedisURI redisUri = RedisURI.builder()
                .withHost("localhost")
                .withPort(6379)
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP3)
                .build();

        redisClient = RedisClient.create(redisUri);
        redisClient.setOptions(clientOptions);

        connection = redisClient.connect();

        // Enable client-side caching with TRACKING
        try {
            StatefulRedisConnection<String, String> trackingConnection = redisClient.connect();
            cacheFrontend = ClientSideCaching.enable(
                    CacheAccessor.forMap(localCache),
                    trackingConnection,
                    TrackingArgs.Builder.enabled().bcast()
            );
        } catch (Exception e) {
            System.err.println("Warning: TRACKING not available, using basic caching");
            cacheFrontend = null;
        }

        // Populate initial data
        populateData();
    }

    private void populateData() {
        RedisCommands<String, String> commands = connection.sync();
        Map<String, String> data = new HashMap<>();

        for (int i = 0; i < NUM_RECORDS; i++) {
            String key = KEY_PREFIX + i;
            String value = String.format(
                "{\"id\":%d,\"email\":\"user%d@example.com\",\"name\":\"User %d\"}",
                i, i, i
            );
            data.put(key, value);
        }

        commands.mset(data);

        // Pre-populate local cache with first half
        for (int i = 0; i < NUM_RECORDS / 2; i++) {
            String key = KEY_PREFIX + i;
            localCache.put(key, data.get(key));
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Clean up benchmark data
        RedisCommands<String, String> commands = connection.sync();
        var keys = commands.keys(KEY_PREFIX + "*");
        if (!keys.isEmpty()) {
            commands.del(keys.toArray(new String[0]));
        }

        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    /**
     * Measures local cache hit latency.
     * This is a pure ConcurrentHashMap lookup - sub-microsecond.
     */
    @Benchmark
    public void localCacheHit(Blackhole bh) {
        // Access record in local cache (first half of data)
        int id = (int) (Math.random() * (NUM_RECORDS / 2));
        String key = KEY_PREFIX + id;
        String value = localCache.get(key);
        bh.consume(value);
    }

    /**
     * Measures cache miss with Redis network fetch.
     */
    @Benchmark
    public void cacheMissFetch(Blackhole bh) {
        // Access record NOT in local cache (second half of data)
        int id = NUM_RECORDS / 2 + (int) (Math.random() * (NUM_RECORDS / 2));
        String key = KEY_PREFIX + id;
        
        // Check local cache first (will miss)
        String value = localCache.get(key);
        if (value == null) {
            // Fetch from Redis
            RedisCommands<String, String> commands = connection.sync();
            value = commands.get(key);
        }
        bh.consume(value);
    }

    /**
     * Measures Redis GET latency (network round-trip).
     */
    @Benchmark
    public void redisGet(Blackhole bh) {
        int id = (int) (Math.random() * NUM_RECORDS);
        String key = KEY_PREFIX + id;
        RedisCommands<String, String> commands = connection.sync();
        String value = commands.get(key);
        bh.consume(value);
    }

    /**
     * Measures Redis SET latency (write operation).
     */
    @Benchmark
    public void redisSet(Blackhole bh) {
        int id = (int) (Math.random() * NUM_RECORDS);
        String key = KEY_PREFIX + id;
        String value = String.format(
            "{\"id\":%d,\"email\":\"updated%d@example.com\",\"name\":\"Updated User %d\"}",
            id, id, id
        );
        RedisCommands<String, String> commands = connection.sync();
        String result = commands.set(key, value);
        bh.consume(result);
    }

    /**
     * Measures CacheFrontend get (with automatic cache-aside).
     */
    @Benchmark
    public void cacheFrontendGet(Blackhole bh) {
        if (cacheFrontend == null) {
            // Fallback if TRACKING not available
            redisGet(bh);
            return;
        }
        
        int id = (int) (Math.random() * NUM_RECORDS);
        String key = KEY_PREFIX + id;
        String value = cacheFrontend.get(key);
        bh.consume(value);
    }

    /**
     * Measures bulk read using MGET.
     */
    @Benchmark
    public void redisMget(Blackhole bh) {
        String[] keys = new String[100];
        int startId = (int) (Math.random() * (NUM_RECORDS - 100));
        for (int i = 0; i < 100; i++) {
            keys[i] = KEY_PREFIX + (startId + i);
        }
        
        RedisCommands<String, String> commands = connection.sync();
        var values = commands.mget(keys);
        bh.consume(values);
    }

    /**
     * Measures bulk write using MSET.
     */
    @Benchmark
    public void redisMset(Blackhole bh) {
        Map<String, String> data = new HashMap<>();
        int startId = (int) (Math.random() * (NUM_RECORDS - 100));
        
        for (int i = 0; i < 100; i++) {
            int id = startId + i;
            String key = KEY_PREFIX + id;
            String value = String.format(
                "{\"id\":%d,\"email\":\"bulk%d@example.com\",\"name\":\"Bulk User %d\"}",
                id, id, id
            );
            data.put(key, value);
        }
        
        RedisCommands<String, String> commands = connection.sync();
        String result = commands.mset(data);
        bh.consume(result);
    }

    /**
     * Measures pipeline of multiple GET operations.
     */
    @Benchmark
    public void redisPipelineGet(Blackhole bh) {
        var asyncCommands = connection.async();
        asyncCommands.setAutoFlushCommands(false);
        
        var futures = new java.util.ArrayList<io.lettuce.core.RedisFuture<String>>();
        int startId = (int) (Math.random() * (NUM_RECORDS - 10));
        
        for (int i = 0; i < 10; i++) {
            futures.add(asyncCommands.get(KEY_PREFIX + (startId + i)));
        }
        
        asyncCommands.flushCommands();
        
        for (var future : futures) {
            try {
                bh.consume(future.get());
            } catch (Exception e) {
                // Ignore
            }
        }
        
        asyncCommands.setAutoFlushCommands(true);
    }
}
