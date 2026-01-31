package com.devara.hollow.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.support.caching.CacheAccessor;
import io.lettuce.core.support.caching.CacheFrontend;
import io.lettuce.core.support.caching.ClientSideCaching;
import io.lettuce.core.TrackingArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Redis configuration with client-side caching using RESP3 TRACKING.
 * 
 * This enables automatic cache invalidation when data changes on the server.
 * The client maintains a local ConcurrentHashMap that gets invalidated
 * via Redis's pub/sub mechanism when keys are modified.
 */
@Configuration
@Profile("redis")
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    /**
     * Local cache backed by ConcurrentHashMap.
     * This is automatically invalidated when Redis TRACKING detects changes.
     */
    @Bean
    public Map<String, String> localCache() {
        return new ConcurrentHashMap<>();
    }

    /**
     * Creates a RedisClient configured for RESP3 protocol.
     * RESP3 is required for client-side caching with TRACKING.
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withDatabase(redisDatabase);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            uriBuilder.withPassword(redisPassword.toCharArray());
        }

        RedisURI redisUri = uriBuilder.build();

        // Configure client for RESP3 protocol (required for TRACKING)
        ClientOptions clientOptions = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP3)
                .build();

        RedisClient client = RedisClient.create(redisUri);
        client.setOptions(clientOptions);

        return client;
    }

    /**
     * Stateful Redis connection for general operations.
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
        return redisClient.connect();
    }

    /**
     * Client-side caching frontend with automatic invalidation.
     * 
     * Uses Redis TRACKING feature to receive invalidation messages
     * when cached keys are modified by any client.
     */
    @Bean
    public CacheFrontend<String, String> cacheFrontend(
            RedisClient redisClient,
            Map<String, String> localCache) {
        
        StatefulRedisConnection<String, String> connection = redisClient.connect();

        // Enable client-side caching with TRACKING
        // BCAST mode broadcasts invalidations for all keys
        CacheFrontend<String, String> cacheFrontend = ClientSideCaching.enable(
                CacheAccessor.forMap(localCache),
                connection,
                TrackingArgs.Builder.enabled().bcast()
        );

        return cacheFrontend;
    }
}
