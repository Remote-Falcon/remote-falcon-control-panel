package com.remotefalcon.controlpanel.controller;

import com.remotefalcon.controlpanel.aop.RequiresAccess;
import com.remotefalcon.controlpanel.response.GitHubIssueResponse;
import com.remotefalcon.controlpanel.service.ControlPanelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ControlPanelController {
  private final ControlPanelService controlPanelService;

  @GetMapping(value = "/controlPanel/gitHubIssues")
  @RequiresAccess
  public ResponseEntity<List<GitHubIssueResponse>> gitHubIssues() {
    return this.controlPanelService.gitHubIssues();
  }

  @GetMapping(value = "/controlPanel/getJwt")
  public ResponseEntity<String> getJwt() {
    return this.controlPanelService.getJwt();
  }
}
