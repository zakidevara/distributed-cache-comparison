package com.devara.hollow.service.s3;

import com.devara.hollow.service.HollowConsumerService;
import com.netflix.hollow.api.consumer.HollowConsumer;
import com.netflix.hollow.api.consumer.HollowConsumer.AbstractRefreshListener;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3-based implementation of HollowConsumerService.
 * Reads Hollow blobs from Amazon S3.
 * Automatically refreshes when new data versions are announced.
 */
@Service
@Profile("s3")
public class S3ConsumerService implements HollowConsumerService {

    private static final Logger log = LoggerFactory.getLogger(S3ConsumerService.class);

    private final S3Client s3Client;
    private HollowConsumer consumer;

    @Value("${hollow.s3.bucket}")
    private String bucketName;

    @Value("${hollow.s3.prefix:hollow}")
    private String blobPrefix;

    public S3ConsumerService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    @PostConstruct
    public void init() {
        log.info("Initializing S3 Hollow Consumer - bucket: {}, prefix: {}", bucketName, blobPrefix);

        S3BlobRetriever blobRetriever = new S3BlobRetriever(s3Client, bucketName, blobPrefix);
        S3AnnouncementWatcher announcementWatcher = new S3AnnouncementWatcher(s3Client, bucketName, blobPrefix);

        consumer = HollowConsumer.withBlobRetriever(blobRetriever)
                .withAnnouncementWatcher(announcementWatcher)
                .withRefreshListener(new AbstractRefreshListener() {
                    @Override
                    public void refreshStarted(long currentVersion, long requestedVersion) {
                        log.info("Consumer refresh started: {} -> {}", currentVersion, requestedVersion);
                    }

                    @Override
                    public void refreshSuccessful(long beforeVersion, long afterVersion, long requestedVersion) {
                        log.info("Consumer refresh successful: {} -> {}", beforeVersion, afterVersion);
                    }

                    @Override
                    public void refreshFailed(long beforeVersion, long afterVersion, long requestedVersion,
                            Throwable failureCause) {
                        log.error("Consumer refresh failed: {} -> {}", beforeVersion, afterVersion, failureCause);
                    }
                })
                .build();

        // Trigger initial refresh
        consumer.triggerRefresh();

        log.info("S3 Hollow Consumer initialized with auto-refresh, version: {}", consumer.getCurrentVersionId());
    }

    @Override
    public HollowConsumer getConsumer() {
        return consumer;
    }
}
