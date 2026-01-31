package com.devara.hollow.service.s3;

import com.netflix.hollow.api.consumer.HollowConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * S3 implementation of HollowConsumer.BlobRetriever.
 * Retrieves Hollow blob files from S3.
 */
public class S3BlobRetriever implements HollowConsumer.BlobRetriever {
    
    private static final Logger log = LoggerFactory.getLogger(S3BlobRetriever.class);
    
    private final S3Client s3Client;
    private final String bucketName;
    private final String prefix;

    public S3BlobRetriever(S3Client s3Client, String bucketName, String prefix) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.prefix = prefix;
    }

    @Override
    public HollowConsumer.Blob retrieveSnapshotBlob(long desiredVersion) {
        String key = String.format("%s/snapshot-%d", prefix, desiredVersion);
        log.info("Retrieving snapshot blob: s3://{}/{}", bucketName, key);
        
        try {
            InputStream is = getObjectInputStream(key);
            return new S3Blob(desiredVersion, is);
        } catch (NoSuchKeyException e) {
            log.warn("Snapshot not found: {}", key);
            return null;
        }
    }

    @Override
    public HollowConsumer.Blob retrieveDeltaBlob(long currentVersion) {
        // Try to find a delta from currentVersion to any newer version
        // For simplicity, we'll look for a delta announcement
        String key = String.format("%s/delta-%d-", prefix, currentVersion);
        log.debug("Looking for delta blob starting with: {}", key);
        
        // In a production implementation, you would list objects with this prefix
        // For now, return null to trigger snapshot retrieval
        return null;
    }

    @Override
    public HollowConsumer.Blob retrieveReverseDeltaBlob(long currentVersion) {
        log.debug("Reverse delta retrieval not implemented, returning null");
        return null;
    }
    
    private InputStream getObjectInputStream(String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest);
        return new BufferedInputStream(response);
    }
    
    /**
     * S3-backed Hollow Blob implementation.
     */
    private static class S3Blob extends HollowConsumer.Blob {
        
        private final InputStream inputStream;
        
        S3Blob(long toVersion, InputStream inputStream) {
            super(toVersion);
            this.inputStream = inputStream;
        }
        
        S3Blob(long fromVersion, long toVersion, InputStream inputStream) {
            super(fromVersion, toVersion);
            this.inputStream = inputStream;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return inputStream;
        }
    }
}
