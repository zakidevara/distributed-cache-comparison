package com.devara.hollow.service.redis;

import com.devara.hollow.model.UserAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.support.caching.CacheFrontend;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redis Consumer Service with client-side caching.
 * 
 * Uses Lettuce's CacheFrontend which provides:
 * - Automatic local cache population on cache miss
 * - Automatic invalidation via Redis TRACKING
 * - Thread-safe ConcurrentHashMap backing
 * 
 * Read pattern:
 * 1. Check local cache first (sub-microsecond)
 * 2. If miss, fetch from Redis (network round-trip)
 * 3. Populate local cache
 * 4. Return result
 * 
 * When any client updates a key, Redis TRACKING
 * broadcasts invalidation to all subscribed clients.
 */
@Service
@Profile("redis")
@RequiredArgsConstructor
@Slf4j
public class RedisConsumerService {

    private static final String KEY_PREFIX = "user:";

    private final CacheFrontend<String, String> cacheFrontend;
    private final StatefulRedisConnection<String, String> connection;
    private final Map<String, String> localCache;
    private final ObjectMapper objectMapper;

    /**
     * Gets a user account by ID using client-side caching.
     * Returns from local cache if available, otherwise fetches from Redis.
     *
     * @param userId the user ID to look up
     * @return the user account, or empty if not found
     */
    public Optional<UserAccount> getById(int userId) {
        String key = KEY_PREFIX + userId;
        
        try {
            // CacheFrontend handles cache-aside pattern automatically
            String value = cacheFrontend.get(key);
            
            if (value == null) {
                return Optional.empty();
            }
            
            return Optional.of(objectMapper.readValue(value, UserAccount.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize user account: {}", userId, e);
            return Optional.empty();
        }
    }

    /**
     * Gets a user account directly from local cache only.
     * Does NOT fetch from Redis if not in local cache.
     *
     * @param userId the user ID to look up
     * @return the user account from local cache, or empty if not cached
     */
    public Optional<UserAccount> getFromLocalCache(int userId) {
        String key = KEY_PREFIX + userId;
        String value = localCache.get(key);
        
        if (value == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(objectMapper.readValue(value, UserAccount.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize user from local cache: {}", userId, e);
            return Optional.empty();
        }
    }

    /**
     * Gets multiple user accounts by their IDs.
     * Uses MGET for efficient bulk fetching.
     *
     * @param userIds the list of user IDs to look up
     * @return list of found user accounts
     */
    public List<UserAccount> getByIds(List<Integer> userIds) {
        List<UserAccount> results = new ArrayList<>();
        List<String> keysToFetch = new ArrayList<>();
        
        // First check local cache
        for (Integer userId : userIds) {
            String key = KEY_PREFIX + userId;
            String cached = localCache.get(key);
            
            if (cached != null) {
                try {
                    results.add(objectMapper.readValue(cached, UserAccount.class));
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize cached user: {}", userId, e);
                    keysToFetch.add(key);
                }
            } else {
                keysToFetch.add(key);
            }
        }
        
        // Fetch missing from Redis
        if (!keysToFetch.isEmpty()) {
            RedisCommands<String, String> commands = connection.sync();
            List<String> values = commands.mget(keysToFetch.toArray(new String[0]))
                    .stream()
                    .map(kv -> kv.getValue())
                    .toList();
            
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                if (value != null) {
                    try {
                        UserAccount user = objectMapper.readValue(value, UserAccount.class);
                        results.add(user);
                        // Populate local cache
                        localCache.put(keysToFetch.get(i), value);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize user from Redis", e);
                    }
                }
            }
        }
        
        return results;
    }

    /**
     * Gets all user accounts from Redis.
     *
     * @return list of all user accounts
     */
    public List<UserAccount> getAll() {
        RedisCommands<String, String> commands = connection.sync();
        var keys = commands.keys(KEY_PREFIX + "*");
        
        if (keys.isEmpty()) {
            return List.of();
        }
        
        List<UserAccount> results = new ArrayList<>();
        List<String> values = commands.mget(keys.toArray(new String[0]))
                .stream()
                .map(kv -> kv.getValue())
                .toList();
        
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value != null) {
                try {
                    results.add(objectMapper.readValue(value, UserAccount.class));
                    // Populate local cache
                    localCache.put(keys.get(i), value);
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize user from Redis", e);
                }
            }
        }
        
        return results;
    }

    /**
     * Gets statistics about the local cache.
     *
     * @return cache statistics map
     */
    public Map<String, Object> getCacheStats() {
        return Map.of(
            "localCacheSize", localCache.size(),
            "localCacheKeys", localCache.keySet().stream().limit(10).toList()
        );
    }

    /**
     * Checks if a user exists in the local cache.
     *
     * @param userId the user ID to check
     * @return true if in local cache
     */
    public boolean isInLocalCache(int userId) {
        return localCache.containsKey(KEY_PREFIX + userId);
    }

    /**
     * Gets the local cache size.
     *
     * @return number of entries in local cache
     */
    public int getLocalCacheSize() {
        return localCache.size();
    }

    /**
     * Manually clears the local cache.
     * Note: TRACKING invalidations happen automatically.
     */
    public void clearLocalCache() {
        localCache.clear();
        log.info("Local cache cleared manually");
    }
}
