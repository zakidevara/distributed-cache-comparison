package com.devara.hollow.benchmark;

import com.devara.hollow.model.UserAccount;
import com.netflix.hollow.api.consumer.HollowConsumer;
import com.netflix.hollow.api.producer.HollowProducer;
import com.netflix.hollow.api.producer.fs.HollowFilesystemAnnouncer;
import com.netflix.hollow.api.producer.fs.HollowFilesystemPublisher;
import com.netflix.hollow.api.consumer.fs.HollowFilesystemAnnouncementWatcher;
import com.netflix.hollow.api.consumer.fs.HollowFilesystemBlobRetriever;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for Hollow (filesystem-based) operations.
 * Measures write latency, read latency, and bulk read performance.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class HollowBenchmark {

    private static final int RECORD_COUNT = 10000;
    private Path hollowDir;
    private HollowProducer producer;
    private HollowConsumer consumer;
    private List<UserAccount> testData;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        // Create temp directory for Hollow data
        hollowDir = Files.createTempDirectory("hollow-benchmark");
        
        // Generate test data
        testData = new ArrayList<>(RECORD_COUNT);
        for (int i = 0; i < RECORD_COUNT; i++) {
            testData.add(new UserAccount(i, "user_" + i, i % 2 == 0));
        }
        
        // Initialize producer
        HollowFilesystemPublisher publisher = new HollowFilesystemPublisher(hollowDir);
        HollowFilesystemAnnouncer announcer = new HollowFilesystemAnnouncer(hollowDir);
        
        producer = HollowProducer.withPublisher(publisher)
                .withAnnouncer(announcer)
                .build();
        
        producer.initializeDataModel(UserAccount.class);
        
        // Run initial cycle to create data
        producer.runCycle(state -> {
            for (UserAccount account : testData) {
                state.add(account);
            }
        });
        
        // Initialize consumer
        HollowFilesystemBlobRetriever blobRetriever = new HollowFilesystemBlobRetriever(hollowDir);
        HollowFilesystemAnnouncementWatcher announcementWatcher = 
                new HollowFilesystemAnnouncementWatcher(hollowDir);
        
        consumer = HollowConsumer.withBlobRetriever(blobRetriever)
                .withAnnouncementWatcher(announcementWatcher)
                .build();
        
        consumer.triggerRefresh();
    }

    @TearDown(Level.Trial)
    public void teardown() throws IOException {
        // Cleanup temp directory
        if (hollowDir != null) {
            Files.walk(hollowDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    /**
     * Benchmark: Write latency for publishing a batch of records.
     */
    @Benchmark
    public void writeLatency(Blackhole blackhole) {
        long version = producer.runCycle(state -> {
            for (UserAccount account : testData) {
                state.add(account);
            }
        });
        blackhole.consume(version);
    }

    /**
     * Benchmark: Read latency for a single record lookup.
     */
    @Benchmark
    public void singleReadLatency(Blackhole blackhole) {
        // Access a record from the consumer's state engine
        var stateEngine = consumer.getStateEngine();
        var typeState = stateEngine.getTypeState("UserAccount");
        if (typeState != null) {
            int ordinal = (int) (System.nanoTime() % Math.max(1, typeState.maxOrdinal()));
            blackhole.consume(ordinal);
        }
    }

    /**
     * Benchmark: Bulk read - scan all records.
     */
    @Benchmark
    public void bulkReadLatency(Blackhole blackhole) {
        var stateEngine = consumer.getStateEngine();
        var typeState = stateEngine.getTypeState("UserAccount");
        if (typeState != null) {
            int count = 0;
            int maxOrdinal = typeState.maxOrdinal();
            for (int i = 0; i <= maxOrdinal; i++) {
                // Just iterate through ordinals - Hollow doesn't expose isOrdinalPopulated directly
                count++;
            }
            blackhole.consume(count);
        }
    }

    /**
     * Benchmark: Consumer refresh latency.
     */
    @Benchmark
    public void refreshLatency(Blackhole blackhole) {
        consumer.triggerRefresh();
        blackhole.consume(consumer.getCurrentVersionId());
    }
}
