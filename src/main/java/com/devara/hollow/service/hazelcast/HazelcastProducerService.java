package com.devara.hollow.service.hazelcast;

import com.devara.hollow.model.UserAccount;
import com.devara.hollow.service.HollowProducerService;
import com.hazelcast.map.IMap;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hazelcast-based producer service that writes UserAccount records to a distributed IMap.
 * 
 * When records are written:
 * 1. Data is stored in the distributed Hazelcast map
 * 2. Near-Cache on all clients is automatically invalidated
 * 3. Next read fetches fresh data and populates Near-Cache
 * 
 * No manual refresh needed - invalidation propagates automatically!
 */
@Service
@Profile("hazelcast")
public class HazelcastProducerService implements HollowProducerService {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastProducerService.class);

    private final IMap<Long, UserAccount> userAccountsMap;

    @Autowired
    public HazelcastProducerService(IMap<Long, UserAccount> userAccountsMap) {
        this.userAccountsMap = userAccountsMap;
    }

    @Override
    @PostConstruct
    public void init() {
        logger.info("Hazelcast producer service initialized with map: {}", userAccountsMap.getName());
    }

    @Override
    public void runCycle(List<UserAccount> data) {
        logger.info("Publishing {} UserAccount records to Hazelcast", data.size());
        
        long startTime = System.currentTimeMillis();
        
        // Convert list to map for bulk put
        Map<Long, UserAccount> records = data.stream()
                .collect(Collectors.toMap(UserAccount::getId, account -> account));
        
        // putAll is more efficient than individual puts
        userAccountsMap.putAll(records);
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Hazelcast publish complete: {} records in {}ms (Near-Cache invalidated on all clients)", 
                data.size(), duration);
    }

    /**
     * Publish a single UserAccount record.
     * 
     * @param account the UserAccount to publish
     */
    public void publish(UserAccount account) {
        userAccountsMap.put(account.getId(), account);
        logger.debug("Published UserAccount {} to Hazelcast", account.getId());
    }

    /**
     * Delete a UserAccount by ID.
     * This removes the entry from the map and invalidates Near-Cache on all clients.
     * 
     * @param userId the ID of the user to delete
     */
    public void delete(long userId) {
        UserAccount removed = userAccountsMap.remove(userId);
        if (removed != null) {
            logger.info("Deleted UserAccount {} from Hazelcast", userId);
        } else {
            logger.warn("UserAccount {} not found for deletion", userId);
        }
    }

    /**
     * Clear all data from the map.
     */
    public void clear() {
        userAccountsMap.clear();
        logger.info("Cleared all UserAccount records from Hazelcast");
    }

    /**
     * Get the current size of the distributed map.
     */
    public int size() {
        return userAccountsMap.size();
    }
}
