package com.devara.hollow.service.filesystem;

import com.devara.hollow.service.HollowConsumerService;
import com.netflix.hollow.api.consumer.HollowConsumer;
import com.netflix.hollow.api.consumer.HollowConsumer.AbstractRefreshListener;
import com.netflix.hollow.api.consumer.fs.HollowFilesystemAnnouncementWatcher;
import com.netflix.hollow.api.consumer.fs.HollowFilesystemBlobRetriever;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Filesystem-based implementation of HollowConsumerService.
 * Reads Hollow blobs from a local directory.
 * Automatically refreshes when new data versions are announced.
 */
@Service
@Profile("filesystem")
public class FilesystemConsumerService implements HollowConsumerService {

    private static final Logger log = LoggerFactory.getLogger(FilesystemConsumerService.class);

    private HollowConsumer consumer;

    @Value("${hollow.filesystem.path:./hollow-repo}")
    private String publishPath;

    @Override
    @PostConstruct
    public void init() {
        Path publishDir = Paths.get(publishPath);

        log.info("Initializing Filesystem Hollow Consumer from: {}", publishDir.toAbsolutePath());

        HollowFilesystemAnnouncementWatcher announcementWatcher = new HollowFilesystemAnnouncementWatcher(publishDir);

        consumer = HollowConsumer.withBlobRetriever(new HollowFilesystemBlobRetriever(publishDir))
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

        log.info("Filesystem Hollow Consumer initialized with auto-refresh, version: {}",
                consumer.getCurrentVersionId());
    }

    @Override
    public HollowConsumer getConsumer() {
        return consumer;
    }
}
