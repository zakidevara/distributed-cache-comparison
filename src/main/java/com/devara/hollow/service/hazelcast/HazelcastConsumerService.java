package com.devara.hollow.service.hazelcast;

import com.devara.hollow.model.UserAccount;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.LocalMapStats;
import com.hazelcast.nearcache.NearCacheStats;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hazelcast-based consumer service using Near-Cache for ultra-fast local reads.
 * 
 * Near-Cache provides:
 * - Sub-millisecond read latency (data cached locally)
 * - Automatic invalidation when data changes (no manual refresh!)
 * - Eventual consistency with configurable TTL
 * 
 * Unlike Hollow/Kafka, reads hit the local Near-Cache first.
 * If data is not in Near-Cache (miss), it fetches from the cluster and caches locally.
 */
@Service
@Profile("hazelcast")
public class HazelcastConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastConsumerService.class);

    private final IMap<Long, UserAccount> userAccountsMap;
    private final HazelcastInstance hazelcastInstance;

    @Autowired
    public HazelcastConsumerService(IMap<Long, UserAccount> userAccountsMap, 
                                    HazelcastInstance hazelcastInstance) {
        this.userAccountsMap = userAccountsMap;
        this.hazelcastInstance = hazelcastInstance;
    }

    @PostConstruct
    public void init() {
        logger.info("Hazelcast consumer service initialized");
        logger.info("Near-Cache enabled - reads are served locally with automatic invalidation");
    }

    /**
     * Check if the service is ready.
     */
    public boolean isReady() {
        return hazelcastInstance.getLifecycleService().isRunning();
    }

    /**
     * Get a UserAccount by ID.
     * First checks Near-Cache (local), then falls back to cluster if not cached.
     * 
     * @param userId the user ID
     * @return Optional containing the UserAccount if found
     */
    public Optional<UserAccount> findById(long userId) {
        UserAccount account = userAccountsMap.get(userId);
        return Optional.ofNullable(account);
    }

    /**
     * Get all UserAccounts.
     * Note: This fetches all values - for large datasets, use pagination or streaming.
     * 
     * @return list of all UserAccounts
     */
    public List<UserAccount> findAll() {
        return new ArrayList<>(userAccountsMap.values());
    }

    /**
     * Get the count of records in the map.
     * 
     * @return the count
     */
    public long count() {
        return userAccountsMap.size();
    }

    /**
     * Find UserAccounts by ID range using predicates.
     * 
     * @param fromId starting ID (inclusive)
     * @param toId ending ID (inclusive)
     * @return list of UserAccounts in the range
     */
    public List<UserAccount> findByIdRange(long fromId, long toId) {
        return userAccountsMap.values().stream()
                .filter(account -> account.getId() >= fromId && account.getId() <= toId)
                .collect(Collectors.toList());
    }

    /**
     * Find all active UserAccounts.
     * 
     * @return list of active UserAccounts
     */
    public List<UserAccount> findActive() {
        return userAccountsMap.values().stream()
                .filter(UserAccount::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Check if a user exists.
     * 
     * @param userId the user ID
     * @return true if exists
     */
    public boolean exists(long userId) {
        return userAccountsMap.containsKey(userId);
    }

    /**
     * Get Near-Cache statistics for monitoring.
     * 
     * @return map of statistics
     */
    public Map<String, Object> getNearCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        LocalMapStats localStats = userAccountsMap.getLocalMapStats();
        NearCacheStats nearCacheStats = localStats.getNearCacheStats();
        
        if (nearCacheStats != null) {
            stats.put("hits", nearCacheStats.getHits());
            stats.put("misses", nearCacheStats.getMisses());
            stats.put("ownedEntryCount", nearCacheStats.getOwnedEntryCount());
            stats.put("ownedEntryMemoryCost", nearCacheStats.getOwnedEntryMemoryCost());
            stats.put("invalidations", nearCacheStats.getInvalidations());
            stats.put("invalidationRequests", nearCacheStats.getInvalidationRequests());
            
            long total = nearCacheStats.getHits() + nearCacheStats.getMisses();
            if (total > 0) {
                stats.put("hitRatio", (double) nearCacheStats.getHits() / total);
            } else {
                stats.put("hitRatio", 0.0);
            }
        } else {
            stats.put("nearCacheEnabled", false);
        }
        
        // Add general map stats
        stats.put("mapSize", userAccountsMap.size());
        stats.put("localEntryCount", localStats.getOwnedEntryCount());
        
        return stats;
    }

    /**
     * Get the current state of the Hazelcast instance.
     */
    public String getState() {
        return hazelcastInstance.getLifecycleService().isRunning() ? "RUNNING" : "NOT_RUNNING";
    }
}
