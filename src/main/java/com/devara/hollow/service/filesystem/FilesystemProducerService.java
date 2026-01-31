package com.devara.hollow.service.filesystem;

import com.devara.hollow.model.UserAccount;
import com.devara.hollow.service.HollowProducerService;
import com.netflix.hollow.api.producer.HollowProducer;
import com.netflix.hollow.api.producer.fs.HollowFilesystemAnnouncer;
import com.netflix.hollow.api.producer.fs.HollowFilesystemPublisher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Filesystem-based implementation of HollowProducerService.
 * Stores Hollow blobs in a local directory.
 */
@Service
@Profile("filesystem")
public class FilesystemProducerService implements HollowProducerService {
    
    private static final Logger log = LoggerFactory.getLogger(FilesystemProducerService.class);
    
    private HollowProducer producer;
    
    @Value("${hollow.filesystem.path:./hollow-repo}")
    private String publishPath;

    @Override
    @PostConstruct
    public void init() {
        Path publishDir = Paths.get(publishPath);
        File dir = publishDir.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        log.info("Initializing Filesystem Hollow Producer at: {}", publishDir.toAbsolutePath());
        
        producer = HollowProducer.withPublisher(new HollowFilesystemPublisher(publishDir))
                                 .withAnnouncer(new HollowFilesystemAnnouncer(publishDir))
                                 .build();
    }

    @Override
    public void runCycle(List<UserAccount> data) {
        log.info("Running publish cycle with {} records", data.size());
        producer.runCycle(state -> {
            for (UserAccount user : data) {
                state.add(user);
            }
        });
        log.info("Publish cycle completed");
    }
}
