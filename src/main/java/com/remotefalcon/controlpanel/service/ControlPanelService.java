package com.remotefalcon.controlpanel.service;

import com.remotefalcon.controlpanel.response.GitHubIssueResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPanelService {
  private final WebClient gitHubWebClient;

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
}
