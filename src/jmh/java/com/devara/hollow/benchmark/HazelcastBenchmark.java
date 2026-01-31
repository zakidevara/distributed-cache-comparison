package com.devara.hollow.benchmark;

import com.devara.hollow.model.UserAccount;
import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for Hazelcast Near-Cache operations.
 * Measures write latency, Near-Cache read latency (local), and bulk read performance.
 * 
 * Near-Cache provides ultra-fast local reads after the first access.
 * This benchmark measures both cold (cache miss) and warm (cache hit) scenarios.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class HazelcastBenchmark {

    private static final int RECORD_COUNT = 10000;
    private static final String MAP_NAME = "benchmark-user-accounts";

    private HazelcastInstance hazelcastInstance;
    private IMap<Long, UserAccount> userAccountsMap;
    private List<UserAccount> testData;

    @Setup(Level.Trial)
    public void setup() {
        // Generate test data
        testData = new ArrayList<>(RECORD_COUNT);
        for (int i = 0; i < RECORD_COUNT; i++) {
            testData.add(new UserAccount(i, "user_" + i, i % 2 == 0));
        }

        // Configure Hazelcast with Near-Cache
        Config config = new Config();
        config.setClusterName("benchmark-cluster-" + System.currentTimeMillis());
        
        // Disable network for embedded mode
        NetworkConfig networkConfig = config.getNetworkConfig();
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(false);
        
        // Configure map with Near-Cache
        MapConfig mapConfig = new MapConfig(MAP_NAME);
        mapConfig.setBackupCount(0);
        
        NearCacheConfig nearCacheConfig = new NearCacheConfig();
        nearCacheConfig.setName(MAP_NAME);
        nearCacheConfig.setInMemoryFormat(InMemoryFormat.OBJECT);
        nearCacheConfig.setInvalidateOnChange(true);
        nearCacheConfig.setCacheLocalEntries(true);
        
        EvictionConfig evictionConfig = new EvictionConfig();
        evictionConfig.setEvictionPolicy(EvictionPolicy.LRU);
        evictionConfig.setMaxSizePolicy(MaxSizePolicy.ENTRY_COUNT);
        evictionConfig.setSize(RECORD_COUNT * 2);
        nearCacheConfig.setEvictionConfig(evictionConfig);
        
        mapConfig.setNearCacheConfig(nearCacheConfig);
        config.addMapConfig(mapConfig);
        
        // Create instance
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        userAccountsMap = hazelcastInstance.getMap(MAP_NAME);
        
        // Pre-populate data
        Map<Long, UserAccount> bulkData = new HashMap<>();
        for (UserAccount account : testData) {
            bulkData.put(account.getId(), account);
        }
        userAccountsMap.putAll(bulkData);
        
        // Warm up Near-Cache by reading all entries once
        for (long i = 0; i < RECORD_COUNT; i++) {
            userAccountsMap.get(i);
        }
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    /**
     * Benchmark: Write latency for publishing a batch of records.
     */
    @Benchmark
    public void writeLatency(Blackhole blackhole) {
        Map<Long, UserAccount> bulkData = new HashMap<>();
        for (UserAccount account : testData) {
            bulkData.put(account.getId(), account);
        }
        userAccountsMap.putAll(bulkData);
        blackhole.consume(bulkData.size());
    }

    /**
     * Benchmark: Single read latency from Near-Cache (warm cache - should be very fast).
     */
    @Benchmark
    public void singleReadLatency_NearCacheHit(Blackhole blackhole) {
        // Read from Near-Cache (already populated in setup)
        long key = System.nanoTime() % RECORD_COUNT;
        UserAccount value = userAccountsMap.get(key);
        blackhole.consume(value);
    }

    /**
     * Benchmark: Single read with cache miss simulation.
     * We use a key that may or may not be in Near-Cache.
     */
    @Benchmark
    public void singleReadLatency_Random(Blackhole blackhole) {
        long key = (long) (Math.random() * RECORD_COUNT);
        UserAccount value = userAccountsMap.get(key);
        blackhole.consume(value);
    }

    /**
     * Benchmark: Bulk read - iterate all records.
     */
    @Benchmark
    public void bulkReadLatency(Blackhole blackhole) {
        int count = 0;
        for (UserAccount account : userAccountsMap.values()) {
            count++;
            blackhole.consume(account);
        }
        blackhole.consume(count);
    }

    /**
     * Benchmark: Write single record (measures Near-Cache invalidation overhead).
     */
    @Benchmark
    public void singleWriteLatency(Blackhole blackhole) {
        long id = System.nanoTime() % RECORD_COUNT;
        UserAccount account = new UserAccount(id, "updated_" + id, true);
        userAccountsMap.put(id, account);
        blackhole.consume(account);
    }

    /**
     * Benchmark: Check existence (containsKey uses Near-Cache).
     */
    @Benchmark
    public void containsKeyLatency(Blackhole blackhole) {
        long key = System.nanoTime() % RECORD_COUNT;
        boolean exists = userAccountsMap.containsKey(key);
        blackhole.consume(exists);
    }
}
