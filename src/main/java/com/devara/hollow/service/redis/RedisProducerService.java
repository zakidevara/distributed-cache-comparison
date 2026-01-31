package com.devara.hollow.service.redis;

import com.devara.hollow.model.UserAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Producer Service for writing user account data.
 * 
 * Writes data to Redis using JSON serialization.
 * When data is written/updated, Redis TRACKING mechanism
 * automatically notifies all clients with cached copies
 * to invalidate their local caches.
 */
@Service
@Profile("redis")
@RequiredArgsConstructor
@Slf4j
public class RedisProducerService {

    private static final String KEY_PREFIX = "user:";
    
    private final StatefulRedisConnection<String, String> connection;
    private final ObjectMapper objectMapper;

    /**
     * Stores a single user account in Redis.
     * This will trigger cache invalidation for any client
     * that has this key in their local cache.
     *
     * @param userAccount the user account to store
     */
    public void produce(UserAccount userAccount) {
        try {
            String key = KEY_PREFIX + userAccount.getId();
            String value = objectMapper.writeValueAsString(userAccount);
            
            RedisCommands<String, String> commands = connection.sync();
            commands.set(key, value);
            
            log.debug("Produced user {} to Redis", userAccount.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user account: {}", userAccount.getId(), e);
            throw new RuntimeException("Failed to serialize user account", e);
        }
    }

    /**
     * Bulk produces multiple user accounts to Redis.
     * Uses MSET for efficient bulk writes.
     *
     * @param userAccounts the collection of user accounts to store
     */
    public void bulkProduce(Collection<UserAccount> userAccounts) {
        if (userAccounts == null || userAccounts.isEmpty()) {
            return;
        }

        Map<String, String> keyValues = new HashMap<>();
        
        for (UserAccount user : userAccounts) {
            try {
                String key = KEY_PREFIX + user.getId();
                String value = objectMapper.writeValueAsString(user);
                keyValues.put(key, value);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize user account: {}", user.getId(), e);
            }
        }

        if (!keyValues.isEmpty()) {
            RedisCommands<String, String> commands = connection.sync();
            commands.mset(keyValues);
            log.info("Bulk produced {} users to Redis", keyValues.size());
        }
    }

    /**
     * Updates an existing user account in Redis.
     * This triggers automatic invalidation of cached copies.
     *
     * @param userAccount the user account to update
     */
    public void update(UserAccount userAccount) {
        produce(userAccount); // Same operation, different semantic
        log.info("Updated user {} in Redis (clients will be invalidated)", userAccount.getId());
    }

    /**
     * Deletes a user account from Redis.
     * This triggers automatic invalidation of cached copies.
     *
     * @param userId the user ID to delete
     */
    public void delete(int userId) {
        String key = KEY_PREFIX + userId;
        RedisCommands<String, String> commands = connection.sync();
        commands.del(key);
        log.info("Deleted user {} from Redis", userId);
    }

    /**
     * Clears all user accounts from Redis.
     * Warning: This removes all keys with the user: prefix.
     */
    public void clearAll() {
        RedisCommands<String, String> commands = connection.sync();
        var keys = commands.keys(KEY_PREFIX + "*");
        if (!keys.isEmpty()) {
            commands.del(keys.toArray(new String[0]));
            log.info("Cleared {} users from Redis", keys.size());
        }
    }

    /**
     * Gets the count of user accounts in Redis.
     *
     * @return the number of user accounts
     */
    public long count() {
        RedisCommands<String, String> commands = connection.sync();
        var keys = commands.keys(KEY_PREFIX + "*");
        return keys.size();
    }
}
