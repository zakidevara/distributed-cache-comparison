package com.devara.hollow;

import com.devara.hollow.model.UserAccount;
import com.devara.hollow.service.hazelcast.HazelcastConsumerService;
import com.devara.hollow.service.hazelcast.HazelcastProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for Hazelcast Near-Cache operations.
 * Provides endpoints for publishing and querying UserAccounts via Hazelcast IMap.
 * Active only when 'hazelcast' profile is enabled.
 */
@RestController
@RequestMapping("/api/hazelcast")
@Profile("hazelcast")
public class HazelcastController {

    private final HazelcastProducerService producerService;
    private final HazelcastConsumerService consumerService;

    @Autowired
    public HazelcastController(HazelcastProducerService producerService, 
                               HazelcastConsumerService consumerService) {
        this.producerService = producerService;
        this.consumerService = consumerService;
    }

    /**
     * Publish user accounts to Hazelcast.
     * Near-Cache on all clients is automatically invalidated - no refresh needed!
     */
    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publishData(@RequestBody(required = false) List<UserAccount> users) {
        if (users == null || users.isEmpty()) {
            users = generateSampleData();
        }

        producerService.runCycle(users);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Data published to Hazelcast");
        response.put("recordCount", users.size());
        response.put("note", "Near-Cache automatically invalidated on all clients - no refresh needed!");
        return ResponseEntity.ok(response);
    }

    /**
     * Publish a single user account.
     */
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody UserAccount user) {
        producerService.publish(user);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User published to Hazelcast");
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a user.
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable long userId) {
        producerService.delete(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User deleted from Hazelcast");
        response.put("userId", userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all users.
     * Reads from Near-Cache when available - ultra-fast!
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserAccount>> getAllUsers() {
        return ResponseEntity.ok(consumerService.findAll());
    }

    /**
     * Get a user by ID.
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserAccount> getUserById(@PathVariable long userId) {
        return consumerService.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get users within an ID range.
     */
    @GetMapping("/users/range")
    public ResponseEntity<List<UserAccount>> getUsersInRange(
            @RequestParam long from,
            @RequestParam long to) {
        return ResponseEntity.ok(consumerService.findByIdRange(from, to));
    }

    /**
     * Get all active users.
     */
    @GetMapping("/users/active")
    public ResponseEntity<List<UserAccount>> getActiveUsers() {
        return ResponseEntity.ok(consumerService.findActive());
    }

    /**
     * Get the current status and Near-Cache statistics.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("state", consumerService.getState());
        response.put("ready", consumerService.isReady());
        response.put("count", consumerService.count());
        response.put("nearCacheStats", consumerService.getNearCacheStats());
        response.put("note", "Near-Cache auto-invalidates - always shows latest data!");
        return ResponseEntity.ok(response);
    }

    /**
     * Clear all data.
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearData() {
        producerService.clear();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "All data cleared from Hazelcast");
        return ResponseEntity.ok(response);
    }

    /**
     * Generate sample user data.
     */
    private List<UserAccount> generateSampleData() {
        List<UserAccount> users = new ArrayList<>();
        users.add(new UserAccount(1, "alice", true));
        users.add(new UserAccount(2, "bob", true));
        users.add(new UserAccount(3, "charlie", false));
        users.add(new UserAccount(4, "diana", true));
        users.add(new UserAccount(5, "eve", false));
        return users;
    }
}
