package com.remotefalcon.controlpanel.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class S3ClientConfig {
    @Value("${s3.endpoint}")
    private String s3Endpoint;
    @Value("${s3.accessKey}")
    private String s3AccessKey;
    @Value("${s3.secretKey}")
    private String s3SecretKey;

    @Bean
    public S3Client amazonS3Client() {
        if (!StringUtils.hasText(s3Endpoint)) {
            throw new IllegalStateException("s3.endpoint must be set (including scheme, e.g. https://nyc3.digitaloceanspaces.com)");
        }
        String normalizedEndpoint = s3Endpoint.contains("://") ? s3Endpoint : "https://" + s3Endpoint;
        AwsBasicCredentials credentials = AwsBasicCredentials.create(s3AccessKey, s3SecretKey);

        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(normalizedEndpoint))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
    }
}
