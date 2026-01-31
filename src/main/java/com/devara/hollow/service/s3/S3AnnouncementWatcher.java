package com.devara.hollow.service.s3;

import com.netflix.hollow.api.consumer.HollowConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * S3 implementation of HollowConsumer.AnnouncementWatcher.
 * Watches for version announcements stored in S3.
 */
public class S3AnnouncementWatcher implements HollowConsumer.AnnouncementWatcher {
    
    private static final Logger log = LoggerFactory.getLogger(S3AnnouncementWatcher.class);
    private static final String VERSION_FILE = "announced.version";
    
    private final S3Client s3Client;
    private final String bucketName;
    private final String prefix;

    public S3AnnouncementWatcher(S3Client s3Client, String bucketName, String prefix) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.prefix = prefix;
    }

    @Override
    public long getLatestVersion() {
        String key = prefix + "/" + VERSION_FILE;
        
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            
            try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest)) {
                String versionStr = new String(response.readAllBytes(), StandardCharsets.UTF_8).trim();
                long version = Long.parseLong(versionStr);
                log.debug("Latest announced version: {}", version);
                return version;
            }
        } catch (NoSuchKeyException e) {
            log.info("No announced version found at s3://{}/{}", bucketName, key);
            return HollowConsumer.AnnouncementWatcher.NO_ANNOUNCEMENT_AVAILABLE;
        } catch (IOException | NumberFormatException e) {
            log.error("Error reading announced version", e);
            return HollowConsumer.AnnouncementWatcher.NO_ANNOUNCEMENT_AVAILABLE;
        }
    }

    @Override
    public void subscribeToUpdates(HollowConsumer consumer) {
        // For production use, implement polling or use S3 event notifications
        // For this demo, consumers need to manually call refresh()
        log.info("S3 announcement watcher initialized. Call refresh() to check for updates.");
    }
}
