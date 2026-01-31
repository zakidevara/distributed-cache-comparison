package com.devara.hollow.config;

import com.devara.hollow.model.UserAccount;
import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Hazelcast configuration for Near-Cache based auto-refresh.
 * 
 * Near-Cache provides:
 * - Local in-memory caching for ultra-fast reads (sub-millisecond)
 * - Automatic invalidation when data changes on any cluster member
 * - No manual refresh needed - invalidation propagates automatically
 * 
 * Active only when 'hazelcast' profile is enabled.
 */
@Configuration
@Profile("hazelcast")
public class HazelcastConfig {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastConfig.class);

    public static final String USER_ACCOUNTS_MAP = "user-accounts";

    @Value("${hazelcast.cluster.name:hollow-hazelcast}")
    private String clusterName;

    @Value("${hazelcast.near-cache.max-size:10000}")
    private int nearCacheMaxSize;

    @Value("${hazelcast.near-cache.ttl-seconds:600}")
    private int nearCacheTtlSeconds;

    @Value("${hazelcast.near-cache.max-idle-seconds:300}")
    private int nearCacheMaxIdleSeconds;

    /**
     * Create and configure the Hazelcast instance.
     */
    @Bean
    public HazelcastInstance hazelcastInstance() {
        Config config = new Config();
        config.setClusterName(clusterName);
        
        // Configure network for local development (disable multicast, use localhost)
        NetworkConfig networkConfig = config.getNetworkConfig();
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig()
                .setEnabled(true)
                .addMember("127.0.0.1");
        
        // Configure the user-accounts map
        MapConfig mapConfig = new MapConfig(USER_ACCOUNTS_MAP);
        mapConfig.setBackupCount(0); // No backups for local dev (set to 1+ for production)
        mapConfig.setAsyncBackupCount(0);
        
        // Enable statistics for monitoring
        mapConfig.setStatisticsEnabled(true);
        
        // Configure eviction
        EvictionConfig evictionConfig = new EvictionConfig();
        evictionConfig.setEvictionPolicy(EvictionPolicy.LRU);
        evictionConfig.setMaxSizePolicy(MaxSizePolicy.PER_NODE);
        evictionConfig.setSize(50000);
        mapConfig.setEvictionConfig(evictionConfig);
        
        // Configure Near-Cache for local reads with auto-invalidation
        NearCacheConfig nearCacheConfig = new NearCacheConfig();
        nearCacheConfig.setName(USER_ACCOUNTS_MAP);
        nearCacheConfig.setInMemoryFormat(InMemoryFormat.OBJECT);
        nearCacheConfig.setSerializeKeys(false);
        nearCacheConfig.setCacheLocalEntries(true);
        
        // Invalidation: when data changes, near-cache entries are automatically invalidated
        nearCacheConfig.setInvalidateOnChange(true);
        
        // Near-cache eviction settings
        EvictionConfig nearCacheEviction = new EvictionConfig();
        nearCacheEviction.setEvictionPolicy(EvictionPolicy.LRU);
        nearCacheEviction.setMaxSizePolicy(MaxSizePolicy.ENTRY_COUNT);
        nearCacheEviction.setSize(nearCacheMaxSize);
        nearCacheConfig.setEvictionConfig(nearCacheEviction);
        
        // TTL and idle timeout
        nearCacheConfig.setTimeToLiveSeconds(nearCacheTtlSeconds);
        nearCacheConfig.setMaxIdleSeconds(nearCacheMaxIdleSeconds);
        
        mapConfig.setNearCacheConfig(nearCacheConfig);
        config.addMapConfig(mapConfig);
        
        // Create and return the instance
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
        logger.info("Hazelcast instance created with cluster name: {}", clusterName);
        logger.info("Near-Cache configured: maxSize={}, ttl={}s, invalidateOnChange=true", 
                nearCacheMaxSize, nearCacheTtlSeconds);
        
        return instance;
    }

    /**
     * Get the user accounts map with Near-Cache enabled.
     */
    @Bean
    public IMap<Long, UserAccount> userAccountsMap(HazelcastInstance hazelcastInstance) {
        IMap<Long, UserAccount> map = hazelcastInstance.getMap(USER_ACCOUNTS_MAP);
        logger.info("User accounts IMap initialized: {}", USER_ACCOUNTS_MAP);
        return map;
    }
}
