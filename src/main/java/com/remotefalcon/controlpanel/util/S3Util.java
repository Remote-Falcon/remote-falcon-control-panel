package com.remotefalcon.controlpanel.util;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.remotefalcon.controlpanel.model.S3Image;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3Util {
  private final AmazonS3 amazonS3Client;

  private final String bucketName = "remote-falcon-images";
  private final String cdnEndpoint = String.format("https://%s.nyc3.cdn.digitaloceanspaces.com", bucketName);

  public ResponseEntity<String> uploadFile(MultipartFile file, String showSubdomain) {
    String path = String.format("%s/%s", showSubdomain,
        Objects.requireNonNull(file.getOriginalFilename()).toLowerCase());

    List<S3ObjectSummary> objectSummaries = amazonS3Client.listObjectsV2(bucketName, showSubdomain)
        .getObjectSummaries();
    if (objectSummaries.size() >= 50) {
      return ResponseEntity.badRequest().body("You have reached the maximum number of images");
    }

    try {
      InputStream fileInputStream = file.getInputStream();
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentLength(file.getSize());
      metadata.setContentType(file.getContentType());
      PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, path, fileInputStream, metadata)
          .withCannedAcl(CannedAccessControlList.PublicRead);
      amazonS3Client.putObject(putObjectRequest);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return ResponseEntity.ok(file.getOriginalFilename());
  }

  public Boolean downloadFile(String filename, String showSubdomain) {
    String path = String.format("%s/%s", showSubdomain, filename);
    String downloadsPath = Paths.get(System.getProperty("user.home"), "Downloads", filename).toString();

    GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, path);
    try {
      S3Object s3Object = amazonS3Client.getObject(getObjectRequest);
      InputStream inputStream = s3Object.getObjectContent();
      FileOutputStream outputStream = new FileOutputStream(downloadsPath);
      byte[] buffer = new byte[4096];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }
      outputStream.close();
    } catch (SdkClientException | IOException e) {
      return false;
    }
    return true;
  }

  public void deleteFile(String filename, String showSubdomain) {
    String path = String.format("%s/%s", showSubdomain, filename);
    amazonS3Client.deleteObject(bucketName, path);
  }

  public List<S3Image> getImages(String showSubdomain) {
    List<S3Image> s3Images = new ArrayList<>();
    List<S3ObjectSummary> objectSummaries = amazonS3Client.listObjectsV2(bucketName, showSubdomain)
        .getObjectSummaries();
    for (S3ObjectSummary objectSummary : objectSummaries) {
      String key = objectSummary.getKey();
      s3Images.add(S3Image.builder()
          .path(String.format("%s/%s", cdnEndpoint, key))
          .name(Strings.split(key, '/')[1])
          .build());
    }
    return s3Images;
  }
}
