package com.devara.hollow.service;

import com.netflix.hollow.api.consumer.HollowConsumer;

/**
 * Interface for Hollow consumer operations.
 * Implementations can use different storage backends (filesystem, S3, etc.)
 */
public interface HollowConsumerService {
    
    /**
     * Initialize the consumer with the configured storage backend.
     */
    void init();
    
    /**
     * Get the underlying HollowConsumer instance.
     * 
     * @return the HollowConsumer
     */
    HollowConsumer getConsumer();
    
    /**
     * Trigger a refresh to get the latest data version.
     */
    default void refresh() {
        getConsumer().triggerRefresh();
    }
    
    /**
     * Get the current data version.
     * 
     * @return the current version ID
     */
    default long getCurrentVersion() {
        return getConsumer().getCurrentVersionId();
    }
}
