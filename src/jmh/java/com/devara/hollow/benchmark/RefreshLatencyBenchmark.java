package com.devara.hollow.benchmark;

import com.devara.hollow.model.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.netflix.hollow.api.consumer.HollowConsumer;
import com.netflix.hollow.api.consumer.fs.HollowFilesystemAnnouncementWatcher;
import com.netflix.hollow.api.consumer.fs.HollowFilesystemBlobRetriever;
import com.netflix.hollow.api.producer.HollowProducer;
import com.netflix.hollow.api.producer.fs.HollowFilesystemAnnouncer;
import com.netflix.hollow.api.producer.fs.HollowFilesystemPublisher;
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
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH Benchmark comparing refresh latency between Hollow, Kafka KTable, Hazelcast Near-Cache, and Redis.
 * Measures end-to-end time from publish to consumer visibility.
 * 
 * This is the key metric for comparing the four approaches:
 * - Hollow: publish -> file write -> announcement -> poll/watch -> refresh
 * - Kafka: publish -> Kafka broker -> consumer stream -> KTable update
 * - Hazelcast: put -> invalidation event -> next read fetches fresh data
 * - Redis: SET -> TRACKING invalidation -> local cache miss -> fetch fresh data
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class RefreshLatencyBenchmark {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String TOPIC = "refresh-benchmark-users";
    private static final String STORE_NAME = "refresh-benchmark-store";
    private static final String HAZELCAST_MAP_NAME = "refresh-benchmark-users";
    private static final String REDIS_KEY_PREFIX = "refresh:user:";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Path hollowDir;
    private Path kafkaStateDir;
    
    // Hollow components
    private HollowProducer hollowProducer;
    private HollowConsumer hollowConsumer;
    
    // Kafka components
    private KafkaProducer<Long, String> kafkaProducer;
    private KafkaStreams kafkaStreams;
    
    // Hazelcast components
    private HazelcastInstance hazelcastProducerInstance;
    private HazelcastInstance hazelcastConsumerInstance;
    private IMap<Long, UserAccount> hazelcastProducerMap;
    private IMap<Long, UserAccount> hazelcastConsumerMap;
    
    // Redis components
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private Map<String, String> redisLocalCache;
    private CacheFrontend<String, String> redisCacheFrontend;
    
    private AtomicLong recordIdCounter = new AtomicLong(0);

    @Setup(Level.Trial)
    public void setup() throws Exception {
        hollowDir = Files.createTempDirectory("hollow-refresh-benchmark");
        kafkaStateDir = Files.createTempDirectory("kafka-refresh-benchmark");
        
        setupHollow();
        setupKafka();
        setupHazelcast();
        setupRedis();
    }

    private void setupHollow() {
        HollowFilesystemPublisher publisher = new HollowFilesystemPublisher(hollowDir);
        HollowFilesystemAnnouncer announcer = new HollowFilesystemAnnouncer(hollowDir);
        
        hollowProducer = HollowProducer.withPublisher(publisher)
                .withAnnouncer(announcer)
                .build();
        hollowProducer.initializeDataModel(UserAccount.class);
        
        // Initial cycle
        hollowProducer.runCycle(state -> {
            state.add(new UserAccount(0, "initial", true));
        });
        
        HollowFilesystemBlobRetriever blobRetriever = new HollowFilesystemBlobRetriever(hollowDir);
        HollowFilesystemAnnouncementWatcher announcementWatcher = 
                new HollowFilesystemAnnouncementWatcher(hollowDir);
        
        hollowConsumer = HollowConsumer.withBlobRetriever(blobRetriever)
                .withAnnouncementWatcher(announcementWatcher)
                .build();
        hollowConsumer.triggerRefresh();
    }

    private void setupKafka() throws Exception {
        // Create topic
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        
        try (AdminClient admin = AdminClient.create(adminProps)) {
            Set<String> existingTopics = admin.listTopics().names().get();
            if (!existingTopics.contains(TOPIC)) {
                NewTopic newTopic = new NewTopic(TOPIC, 1, (short) 1);
                newTopic.configs(Map.of("cleanup.policy", "compact"));
                admin.createTopics(List.of(newTopic)).all().get();
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not create topic: " + e.getMessage());
        }
        
        // Producer
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        kafkaProducer = new KafkaProducer<>(producerProps);
        
        // Streams
        Properties streamsProps = new Properties();
        streamsProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "refresh-benchmark-" + System.currentTimeMillis());
        streamsProps.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        streamsProps.put(StreamsConfig.STATE_DIR_CONFIG, kafkaStateDir.toString());
        streamsProps.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 50);
        streamsProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        StreamsBuilder builder = new StreamsBuilder();
        builder.table(
                TOPIC,
                Consumed.with(Serdes.Long(), Serdes.String()),
                Materialized.<Long, String, KeyValueStore<Bytes, byte[]>>as(STORE_NAME)
                        .withKeySerde(Serdes.Long())
                        .withValueSerde(Serdes.String())
        );
        
        kafkaStreams = new KafkaStreams(builder.build(), streamsProps);
        
        CountDownLatch latch = new CountDownLatch(1);
        kafkaStreams.setStateListener((newState, oldState) -> {
            if (newState == KafkaStreams.State.RUNNING) {
                latch.countDown();
            }
        });
        
        kafkaStreams.start();
        latch.await(30, TimeUnit.SECONDS);
    }

    private void setupHazelcast() {
        String clusterName = "refresh-benchmark-" + System.currentTimeMillis();
        
        // Configure with Near-Cache (simulates distributed scenario with 2 instances)
        Config config = new Config();
        config.setClusterName(clusterName);
        
        NetworkConfig networkConfig = config.getNetworkConfig();
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        
        // Map config with Near-Cache
        MapConfig mapConfig = new MapConfig(HAZELCAST_MAP_NAME);
        mapConfig.setBackupCount(0);
        
        NearCacheConfig nearCacheConfig = new NearCacheConfig();
        nearCacheConfig.setName(HAZELCAST_MAP_NAME);
        nearCacheConfig.setInMemoryFormat(InMemoryFormat.OBJECT);
        nearCacheConfig.setInvalidateOnChange(true);
        nearCacheConfig.setCacheLocalEntries(true);
        
        EvictionConfig evictionConfig = new EvictionConfig();
        evictionConfig.setEvictionPolicy(EvictionPolicy.LRU);
        evictionConfig.setMaxSizePolicy(MaxSizePolicy.ENTRY_COUNT);
        evictionConfig.setSize(100000);
        nearCacheConfig.setEvictionConfig(evictionConfig);
        
        mapConfig.setNearCacheConfig(nearCacheConfig);
        config.addMapConfig(mapConfig);
        
        // Create two instances to simulate producer/consumer separation
        hazelcastProducerInstance = Hazelcast.newHazelcastInstance(config);
        hazelcastConsumerInstance = Hazelcast.newHazelcastInstance(config);
        
        hazelcastProducerMap = hazelcastProducerInstance.getMap(HAZELCAST_MAP_NAME);
        hazelcastConsumerMap = hazelcastConsumerInstance.getMap(HAZELCAST_MAP_NAME);
        
        // Pre-populate consumer's Near-Cache
        hazelcastProducerMap.put(0L, new UserAccount(0, "initial", true));
        hazelcastConsumerMap.get(0L); // Warm up Near-Cache
    }

    private void setupRedis() {
        redisLocalCache = new ConcurrentHashMap<>();
        
        RedisURI redisUri = RedisURI.builder()
                .withHost("localhost")
                .withPort(6379)
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP3)
                .build();

        redisClient = RedisClient.create(redisUri);
        redisClient.setOptions(clientOptions);

        redisConnection = redisClient.connect();

        // Enable client-side caching with TRACKING
        try {
            StatefulRedisConnection<String, String> trackingConnection = redisClient.connect();
            redisCacheFrontend = ClientSideCaching.enable(
                    CacheAccessor.forMap(redisLocalCache),
                    trackingConnection,
                    TrackingArgs.Builder.enabled().bcast()
            );
        } catch (Exception e) {
            System.err.println("Warning: Redis TRACKING not available: " + e.getMessage());
            redisCacheFrontend = null;
        }

        // Pre-populate initial data
        RedisCommands<String, String> commands = redisConnection.sync();
        String initialJson = "{\"id\":0,\"email\":\"initial\",\"name\":\"Initial\"}";
        commands.set(REDIS_KEY_PREFIX + "0", initialJson);
        redisLocalCache.put(REDIS_KEY_PREFIX + "0", initialJson); // Warm up local cache
    }

    @TearDown(Level.Trial)
    public void teardown() throws IOException {
        if (kafkaProducer != null) kafkaProducer.close();
        if (kafkaStreams != null) kafkaStreams.close();
        if (hazelcastProducerInstance != null) hazelcastProducerInstance.shutdown();
        if (hazelcastConsumerInstance != null) hazelcastConsumerInstance.shutdown();
        
        // Cleanup Redis
        if (redisConnection != null) {
            try {
                RedisCommands<String, String> commands = redisConnection.sync();
                var keys = commands.keys(REDIS_KEY_PREFIX + "*");
                if (!keys.isEmpty()) {
                    commands.del(keys.toArray(new String[0]));
                }
                redisConnection.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        if (redisClient != null) redisClient.shutdown();
        
        cleanupDirectory(hollowDir);
        cleanupDirectory(kafkaStateDir);
    }

    private void cleanupDirectory(Path dir) throws IOException {
        if (dir != null && Files.exists(dir)) {
            Files.walk(dir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    /**
     * Benchmark: Hollow end-to-end refresh latency.
     * Measures time from publish to consumer refresh.
     */
    @Benchmark
    public void hollowRefreshLatency(Blackhole blackhole) {
        long id = recordIdCounter.incrementAndGet();
        UserAccount account = new UserAccount(id, "user_" + id, true);
        
        // Publish
        hollowProducer.runCycle(state -> {
            state.add(account);
        });
        
        // Trigger refresh (simulates what the announcement watcher would do)
        hollowConsumer.triggerRefresh();
        
        blackhole.consume(hollowConsumer.getCurrentVersionId());
    }

    /**
     * Benchmark: Kafka KTable end-to-end refresh latency.
     * Measures time from publish to visibility in KTable store.
     */
    @Benchmark
    public void kafkaRefreshLatency(Blackhole blackhole) throws Exception {
        long id = recordIdCounter.incrementAndGet();
        UserAccount account = new UserAccount(id, "user_" + id, true);
        
        // Publish
        String json = objectMapper.writeValueAsString(account);
        kafkaProducer.send(new ProducerRecord<>(TOPIC, id, json)).get();
        
        // Poll until visible (with timeout)
        ReadOnlyKeyValueStore<Long, String> store = kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(STORE_NAME, QueryableStoreTypes.keyValueStore())
        );
        
        long startWait = System.currentTimeMillis();
        while (store.get(id) == null && (System.currentTimeMillis() - startWait) < 5000) {
            Thread.sleep(1);
        }
        
        blackhole.consume(store.get(id));
    }

    /**
     * Benchmark: Hazelcast Near-Cache end-to-end refresh latency.
     * Measures time from put on producer to visibility on consumer (after invalidation).
     */
    @Benchmark
    public void hazelcastRefreshLatency(Blackhole blackhole) {
        long id = recordIdCounter.incrementAndGet();
        UserAccount account = new UserAccount(id, "user_" + id, true);
        
        // Publish via producer instance
        hazelcastProducerMap.put(id, account);
        
        // Read from consumer instance - Near-Cache should be invalidated automatically
        // The first read after invalidation will fetch from the cluster
        UserAccount result = hazelcastConsumerMap.get(id);
        
        blackhole.consume(result);
    }

    /**
     * Benchmark: Redis client-side cache end-to-end refresh latency.
     * Measures time from SET to visibility via TRACKING invalidation.
     */
    @Benchmark
    public void redisRefreshLatency(Blackhole blackhole) throws Exception {
        long id = recordIdCounter.incrementAndGet();
        String key = REDIS_KEY_PREFIX + id;
        String json = String.format(
            "{\"id\":%d,\"email\":\"user_%d@example.com\",\"name\":\"User %d\"}",
            id, id, id
        );
        
        // Write to Redis (triggers TRACKING invalidation)
        RedisCommands<String, String> commands = redisConnection.sync();
        commands.set(key, json);
        
        // Read via CacheFrontend (will fetch from Redis since not in local cache)
        String result;
        if (redisCacheFrontend != null) {
            result = redisCacheFrontend.get(key);
        } else {
            // Fallback if TRACKING not available
            result = commands.get(key);
        }
        
        blackhole.consume(result);
    }
}
