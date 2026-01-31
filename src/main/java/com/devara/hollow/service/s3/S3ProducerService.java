package com.devara.hollow.service.s3;

import com.devara.hollow.model.UserAccount;
import com.devara.hollow.service.HollowProducerService;
import com.netflix.hollow.api.producer.HollowProducer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

/**
 * S3-based implementation of HollowProducerService.
 * Stores Hollow blobs in Amazon S3.
 */
@Service
@Profile("s3")
public class S3ProducerService implements HollowProducerService {
    
    private static final Logger log = LoggerFactory.getLogger(S3ProducerService.class);
    
    private final S3Client s3Client;
    private HollowProducer producer;
    
    @Value("${hollow.s3.bucket}")
    private String bucketName;
    
    @Value("${hollow.s3.prefix:hollow}")
    private String blobPrefix;

    public S3ProducerService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    @PostConstruct
    public void init() {
        log.info("Initializing S3 Hollow Producer - bucket: {}, prefix: {}", bucketName, blobPrefix);
        
        S3Publisher publisher = new S3Publisher(s3Client, bucketName, blobPrefix);
        S3Announcer announcer = new S3Announcer(s3Client, bucketName, blobPrefix);
        
        producer = HollowProducer.withPublisher(publisher)
                                 .withAnnouncer(announcer)
                                 .build();
        
        log.info("S3 Hollow Producer initialized");
    }

    @Override
    public void runCycle(List<UserAccount> data) {
        log.info("Running S3 publish cycle with {} records", data.size());
        producer.runCycle(state -> {
            for (UserAccount user : data) {
                state.add(user);
            }
        });
        log.info("S3 publish cycle completed");
    }
}
