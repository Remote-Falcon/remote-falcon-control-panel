package com.remotefalcon.controlpanel.configuration;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3ClientConfig {
    @Value("${s3.endpoint}")
    private String s3Endpoint;
    @Value("${s3.accessKey}")
    private String s3AccessKey;
    @Value("${s3.secretKey}")
    private String s3SecretKey;

    @Bean
    public AmazonS3 amazonS3Client() {
        BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials(s3AccessKey, s3SecretKey);

        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(basicAWSCredentials))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3Endpoint, "us-east-1"))
                .build();
    }
}
