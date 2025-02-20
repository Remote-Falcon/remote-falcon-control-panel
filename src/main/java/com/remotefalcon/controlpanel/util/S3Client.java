package com.remotefalcon.controlpanel.util;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
@Getter
public class S3Client {
    @Value("${space.endpoint}")
    private String spaceEndpoint;
    @Value("${space.name}")
    private String spaceName;
    @Value("${space.accessKey}")
    private String spaceAccessKey;
    @Value("${space.secretKey}")
    private String spaceSecretKey;

    public void uploadFile(File file, String showSubdomain) {
        BasicAWSCredentials creds = new BasicAWSCredentials(spaceAccessKey, spaceSecretKey);

        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(creds))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(spaceEndpoint, "us-east-1"))
                .build();

        String path = String.format("%s/%s", showSubdomain, file.getName());

        PutObjectRequest putObjectRequest = new PutObjectRequest(spaceName, path, file)
                .withCannedAcl(CannedAccessControlList.PublicRead);
        s3Client.putObject(putObjectRequest);
    }
}
