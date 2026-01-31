package com.devara.hollow.service;

import com.devara.hollow.model.UserAccount;

import java.util.List;

/**
 * Interface for Hollow producer operations.
 * Implementations can use different storage backends (filesystem, S3, etc.)
 */
public interface HollowProducerService {
    
    /**
     * Initialize the producer with the configured storage backend.
     */
    void init();
    
    /**
     * Run a publish cycle with the given data.
     * This creates a new version in Hollow with all the provided records.
     * 
     * @param data the list of UserAccount objects to publish
     */
    void runCycle(List<UserAccount> data);
}
