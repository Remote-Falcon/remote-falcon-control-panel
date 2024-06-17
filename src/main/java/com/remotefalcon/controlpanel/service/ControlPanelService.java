package com.remotefalcon.controlpanel.service;

import com.github.jasminb.jsonapi.JSONAPIDocument;
import com.patreon.PatreonAPI;
import com.patreon.resources.Campaign;
import com.patreon.resources.Pledge;
import com.remotefalcon.controlpanel.response.GitHubIssueResponse;
import com.remotefalcon.controlpanel.response.Patrons;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPanelService {
  private final WebClient gitHubWebClient;


  @Value("${patreon.access-token}")
  String patreonAccessToken;

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

  public ResponseEntity<List<Patrons>> patrons() {
    try {
      PatreonAPI apiClient = new PatreonAPI(patreonAccessToken);
      JSONAPIDocument<List<Campaign>> campaignsResponse = apiClient.fetchCampaigns();
      List<Campaign> campaigns = campaignsResponse.get();
      List<Pledge> pledgesResponse = apiClient.fetchAllPledges(campaigns.get(0).getId());
      List<Patrons> patrons = new ArrayList<>();
      for(Pledge pledge : pledgesResponse) {
        patrons.add(Patrons.builder()
                .fullName(pledge.getPatron().getFullName())
                .build());
      }
      return ResponseEntity.ok(patrons);
    }catch (IOException ioe) {
      log.info(ioe.getMessage());
    }
    return ResponseEntity.ok(null);
  }
}
