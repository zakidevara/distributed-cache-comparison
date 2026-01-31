package com.devara.hollow.service.s3;

import com.netflix.hollow.api.producer.HollowProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

/**
 * S3 implementation of HollowProducer.Announcer.
 * Announces new versions by writing a version file to S3.
 */
public class S3Announcer implements HollowProducer.Announcer {
    
    private static final Logger log = LoggerFactory.getLogger(S3Announcer.class);
    private static final String VERSION_FILE = "announced.version";
    
    private final S3Client s3Client;
    private final String bucketName;
    private final String prefix;

    public S3Announcer(S3Client s3Client, String bucketName, String prefix) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.prefix = prefix;
    }

    @Override
    public void announce(long stateVersion) {
        String key = prefix + "/" + VERSION_FILE;
        String versionContent = String.valueOf(stateVersion);
        
        log.info("Announcing version {} to s3://{}/{}", stateVersion, bucketName, key);
        
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("text/plain")
                .build();
        
        s3Client.putObject(putRequest, RequestBody.fromString(versionContent, StandardCharsets.UTF_8));
        log.info("Successfully announced version {}", stateVersion);
    }
}
