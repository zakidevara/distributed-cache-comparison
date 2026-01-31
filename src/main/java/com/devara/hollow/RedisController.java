package com.devara.hollow;

import com.devara.hollow.model.UserAccount;
import com.devara.hollow.service.redis.RedisConsumerService;
import com.devara.hollow.service.redis.RedisProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Redis client-side caching operations.
 * 
 * Provides endpoints to:
 * - Produce (write) user data to Redis
 * - Consume (read) user data with client-side caching
 * - View cache statistics
 * - Demo automatic invalidation
 */
@RestController
@RequestMapping("/api/redis")
@Profile("redis")
@RequiredArgsConstructor
public class RedisController {

    private final RedisProducerService producerService;
    private final RedisConsumerService consumerService;

    // ============== Producer Endpoints ==============

    /**
     * Produces a single user account to Redis.
     */
    @PostMapping("/produce")
    public ResponseEntity<Map<String, Object>> produce(@RequestBody UserAccount userAccount) {
        producerService.produce(userAccount);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "User produced to Redis",
            "userId", userAccount.getId()
        ));
    }

    /**
     * Produces sample data for testing.
     */
    @PostMapping("/produce/sample")
    public ResponseEntity<Map<String, Object>> produceSampleData(
            @RequestParam(defaultValue = "100") int count) {
        
        List<UserAccount> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            users.add(new UserAccount(i, "user" + i, true));
        }
        
        producerService.bulkProduce(users);
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Sample data produced to Redis",
            "count", count
        ));
    }

    /**
     * Updates a user account (triggers invalidation).
     */
    @PutMapping("/produce/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable int id,
            @RequestBody UserAccount userAccount) {
        
        userAccount = new UserAccount(id, userAccount.getUsername(), userAccount.isActive());
        producerService.update(userAccount);
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "User updated in Redis (clients invalidated)",
            "userId", id
        ));
    }

    /**
     * Deletes a user account.
     */
    @DeleteMapping("/produce/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable int id) {
        producerService.delete(id);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "User deleted from Redis",
            "userId", id
        ));
    }

    /**
     * Clears all user data from Redis.
     */
    @DeleteMapping("/produce/all")
    public ResponseEntity<Map<String, Object>> clearAll() {
        producerService.clearAll();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "All users cleared from Redis"
        ));
    }

    // ============== Consumer Endpoints ==============

    /**
     * Gets a user by ID using client-side caching.
     */
    @GetMapping("/consume/{id}")
    public ResponseEntity<?> getById(@PathVariable int id) {
        var user = consumerService.getById(id);
        
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(Map.of(
            "user", user.get(),
            "fromLocalCache", consumerService.isInLocalCache(id)
        ));
    }

    /**
     * Gets a user from local cache only (no Redis fetch).
     */
    @GetMapping("/consume/local/{id}")
    public ResponseEntity<?> getFromLocalCache(@PathVariable int id) {
        var user = consumerService.getFromLocalCache(id);
        
        if (user.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "status", "miss",
                "message", "User not in local cache",
                "userId", id
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "status", "hit",
            "user", user.get()
        ));
    }

    /**
     * Gets all users from Redis.
     */
    @GetMapping("/consume/all")
    public ResponseEntity<List<UserAccount>> getAll() {
        return ResponseEntity.ok(consumerService.getAll());
    }

    /**
     * Gets cache statistics.
     */
    @GetMapping("/consume/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(consumerService.getCacheStats());
    }

    /**
     * Clears the local cache manually.
     */
    @PostMapping("/consume/clear-local")
    public ResponseEntity<Map<String, Object>> clearLocalCache() {
        consumerService.clearLocalCache();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Local cache cleared"
        ));
    }

    // ============== Demo Endpoints ==============

    /**
     * Demonstrates automatic cache invalidation.
     * 1. Reads a user (populates local cache)
     * 2. Updates the user (triggers invalidation)
     * 3. Reads again (should fetch fresh data)
     */
    @PostMapping("/demo/invalidation/{id}")
    public ResponseEntity<Map<String, Object>> demoInvalidation(@PathVariable int id) {
        // Step 1: Read to populate local cache
        var beforeRead = consumerService.getById(id);
        boolean wasInCacheBefore = consumerService.isInLocalCache(id);
        
        // Step 2: Update the user (triggers invalidation)
        UserAccount updated = new UserAccount(
            id, 
            "updated_user" + id, 
            true
        );
        producerService.update(updated);
        
        // Small delay to allow invalidation to propagate
        try { Thread.sleep(50); } catch (InterruptedException e) { }
        
        // Step 3: Read again (should get fresh data)
        var afterRead = consumerService.getById(id);
        boolean isInCacheAfter = consumerService.isInLocalCache(id);
        
        return ResponseEntity.ok(Map.of(
            "step1_beforeUpdate", beforeRead.map(u -> Map.of("username", u.getUsername(), "active", u.isActive())).orElse(null),
            "step1_wasInCache", wasInCacheBefore,
            "step2_updated", Map.of("username", updated.getUsername(), "active", updated.isActive()),
            "step3_afterUpdate", afterRead.map(u -> Map.of("username", u.getUsername(), "active", u.isActive())).orElse(null),
            "step3_isInCache", isInCacheAfter,
            "invalidationWorked", afterRead.isPresent() && afterRead.get().getUsername().startsWith("updated")
        ));
    }

    /**
     * Gets the total count of users in Redis.
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getCount() {
        return ResponseEntity.ok(Map.of(
            "redisCount", producerService.count(),
            "localCacheSize", consumerService.getLocalCacheSize()
        ));
    }
}
