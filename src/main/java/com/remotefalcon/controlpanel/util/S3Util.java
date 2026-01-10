package com.remotefalcon.controlpanel.util;

import com.remotefalcon.controlpanel.model.S3Image;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3Util {
  private final S3Client amazonS3Client;

  private final String bucketName = "remote-falcon-images";
  private final String cdnEndpoint = String.format("https://%s.nyc3.cdn.digitaloceanspaces.com", bucketName);

  public ResponseEntity<String> uploadFile(MultipartFile file, String showSubdomain) {
    String path = String.format("%s/%s", showSubdomain,
        Objects.requireNonNull(file.getOriginalFilename()).toLowerCase());

    ListObjectsV2Response listResponse = amazonS3Client.listObjectsV2(ListObjectsV2Request.builder()
        .bucket(bucketName)
        .prefix(showSubdomain)
        .build());
    if (listResponse.contents().size() >= 50) {
      return ResponseEntity.badRequest().body("You have reached the maximum number of images");
    }

    try (InputStream fileInputStream = file.getInputStream()) {
      PutObjectRequest putObjectRequest = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(path)
          .contentType(file.getContentType())
          .acl(ObjectCannedACL.PUBLIC_READ)
          .build();
      amazonS3Client.putObject(putObjectRequest, RequestBody.fromInputStream(fileInputStream, file.getSize()));
    } catch (IOException | S3Exception e) {
      throw new RuntimeException(e);
    }
    return ResponseEntity.ok(file.getOriginalFilename());
  }

  public Boolean downloadFile(String filename, String showSubdomain) {
    String path = String.format("%s/%s", showSubdomain, filename);
    Path downloadsPath = Paths.get(System.getProperty("user.home"), "Downloads", filename);

    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(path)
        .build();
    try {
      amazonS3Client.getObject(getObjectRequest, ResponseTransformer.toFile(downloadsPath));
    } catch (S3Exception e) {
      return false;
    }
    return true;
  }

  public void deleteFile(String filename, String showSubdomain) {
    String path = String.format("%s/%s", showSubdomain, filename);
    amazonS3Client.deleteObject(DeleteObjectRequest.builder()
        .bucket(bucketName)
        .key(path)
        .build());
  }

  public List<S3Image> getImages(String showSubdomain) {
    List<S3Image> s3Images = new ArrayList<>();
    ListObjectsV2Response listResponse = amazonS3Client.listObjectsV2(ListObjectsV2Request.builder()
        .bucket(bucketName)
        .prefix(showSubdomain)
        .build());
    for (S3Object objectSummary : listResponse.contents()) {
      String key = objectSummary.key();
      String name = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
      s3Images.add(S3Image.builder()
          .path(String.format("%s/%s", cdnEndpoint, key))
          .name(name)
          .build());
    }
    return s3Images;
  }
}
