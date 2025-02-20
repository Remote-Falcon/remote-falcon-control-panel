package com.remotefalcon.controlpanel.service;

import com.amazonaws.services.s3.model.PutObjectRequest;
import com.remotefalcon.controlpanel.dto.TokenDTO;
import com.remotefalcon.controlpanel.util.S3Client;
import com.remotefalcon.controlpanel.repository.ShowRepository;
import com.remotefalcon.controlpanel.response.GitHubIssueResponse;
import com.remotefalcon.controlpanel.util.AuthUtil;
import com.remotefalcon.library.documents.Show;
import com.remotefalcon.library.enums.StatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPanelService {
  private final WebClient gitHubWebClient;
  private final AuthUtil authUtil;
  private final ShowRepository showRepository;
  private final HttpServletRequest httpServletRequest;

  private final S3Client s3Client;

  public ResponseEntity<List<GitHubIssueResponse>> gitHubIssues() {
    List<GitHubIssueResponse> ghIssue = this.gitHubWebClient.get()
            .uri("repos/Remote-Falcon/remote-falcon-issue-tracker/issues")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<GitHubIssueResponse>>() {})
            .block();
    if(CollectionUtils.isNotEmpty(ghIssue)) {
      ghIssue.forEach(issue -> {
        boolean isBug = issue.getLabels().stream().anyMatch(label -> StringUtils.equalsIgnoreCase("bug", label.getName()));
        issue.setType(isBug ? "bug" : "enhancement");
      });
    }
    return ResponseEntity.ok(ghIssue);
  }

  public ResponseEntity<String> getJwt() {
    String[] basicAuthCredentials = this.authUtil.getBasicAuthCredentials(httpServletRequest);
    if (basicAuthCredentials != null) {
      String email = basicAuthCredentials[0];
      String password = basicAuthCredentials[1];
      Optional<Show> optionalShow = this.showRepository.findByEmailIgnoreCase(email);
      if (optionalShow.isEmpty()) {
        throw new RuntimeException(StatusResponse.SHOW_NOT_FOUND.name());
      }
      Show show = optionalShow.get();
      BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
      boolean passwordsMatch = passwordEncoder.matches(password, show.getPassword());
      if (passwordsMatch) {
        if (!show.getEmailVerified()) {
          throw new RuntimeException(StatusResponse.EMAIL_NOT_VERIFIED.name());
        }
        String jwt = this.authUtil.signJwt(show);
        return ResponseEntity.ok(jwt);
      }
    }
    throw new RuntimeException(StatusResponse.UNAUTHORIZED.name());
  }

  public ResponseEntity<String> uploadImage(MultipartFile file) {
    Optional<Show> show = this.showRepository.findByShowToken(authUtil.tokenDTO.getShowToken());
    if(show.isEmpty()) {
      throw new RuntimeException(StatusResponse.SHOW_NOT_FOUND.name());
    }

    String imageValidation = this.validateImage(file);
    if(imageValidation != null) {
      return ResponseEntity.badRequest().body(imageValidation);
    }

    File localFile = convertMultipartFileToFile(file);

    s3Client.uploadFile(localFile, show.get().getShowSubdomain());

    return ResponseEntity.ok(file.getOriginalFilename());
  }

  private String validateImage(MultipartFile file) {
    if(!StringUtils.contains(file.getContentType(), "image")) {
      return "File must be an image";
    }
    return null;
  }

  private File convertMultipartFileToFile(MultipartFile file) {
    File convertedFile = new File(Objects.requireNonNull(file.getOriginalFilename()));
    try {
      Files.copy(file.getInputStream(), convertedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return convertedFile;
  }
}
