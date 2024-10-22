package com.remotefalcon.controlpanel.service;

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
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPanelService {
  private final WebClient gitHubWebClient;
  private final AuthUtil authUtil;
  private final ShowRepository showRepository;
  private final HttpServletRequest httpServletRequest;

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
}
