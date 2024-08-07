package com.remotefalcon.controlpanel.dto;

import com.remotefalcon.library.enums.ShowRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenDTO {
  private String showToken;
  private String email;
  private String showSubdomain;
  private ShowRole showRole;
}
