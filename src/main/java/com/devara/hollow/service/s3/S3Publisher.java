package com.devara.hollow.service.s3;

import com.netflix.hollow.api.producer.HollowProducer;
import com.netflix.hollow.api.producer.HollowProducer.Blob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;

/**
 * S3 implementation of HollowProducer.Publisher.
 * Publishes Hollow blob files to S3.
 */
public class S3Publisher implements HollowProducer.Publisher {
    
    private static final Logger log = LoggerFactory.getLogger(S3Publisher.class);
    
    private final S3Client s3Client;
    private final String bucketName;
    private final String prefix;

    public S3Publisher(S3Client s3Client, String bucketName, String prefix) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.prefix = prefix;
    }

    @Override
    public void publish(HollowProducer.PublishArtifact publishArtifact) {
        if (!(publishArtifact instanceof Blob blob)) {
            log.warn("Received non-Blob publish artifact, skipping: {}", publishArtifact.getClass().getName());
            return;
        }
        
        Blob.Type blobType = blob.getType();
        String typeStr = getBlobTypeString(blobType);
        
        String key;
        if (blobType == Blob.Type.SNAPSHOT) {
            key = String.format("%s/%s-%d", prefix, typeStr, blob.getToVersion());
        } else {
            key = String.format("%s/%s-%d-%d", prefix, typeStr, blob.getFromVersion(), blob.getToVersion());
        }
        
        log.info("Publishing {} to s3://{}/{}", typeStr, bucketName, key);
        
        try (InputStream is = blob.newInputStream()) {
            byte[] bytes = is.readAllBytes();
            
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("application/octet-stream")
                    .build();
            
            s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));
            log.info("Successfully published {} ({} bytes)", key, bytes.length);
        } catch (IOException e) {
            throw new RuntimeException("Failed to publish blob to S3: " + key, e);
        }
    }
    
    private String getBlobTypeString(Blob.Type type) {
        return switch (type) {
            case SNAPSHOT -> "snapshot";
            case DELTA -> "delta";
            case REVERSE_DELTA -> "reversedelta";
        };
    }
}
