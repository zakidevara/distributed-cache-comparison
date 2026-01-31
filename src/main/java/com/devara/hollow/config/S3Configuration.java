package com.devara.hollow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Configuration for AWS S3 client.
 * Only active when the 's3' profile is enabled.
 */
@Configuration
@Profile("s3")
public class S3Configuration {
    
    @Value("${hollow.s3.region:us-east-1}")
    private String region;
    
    @Value("${hollow.s3.endpoint:#{null}}")
    private String endpoint;
    
    @Value("${hollow.s3.access-key:#{null}}")
    private String accessKey;
    
    @Value("${hollow.s3.secret-key:#{null}}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region));
        
        // Use custom endpoint if provided (useful for LocalStack, MinIO, etc.)
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                   .forcePathStyle(true);
        }
        
        // Use static credentials if provided, otherwise use default credential chain
        if (accessKey != null && !accessKey.isBlank() && 
            secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            );
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        
        return builder.build();
    }
}
