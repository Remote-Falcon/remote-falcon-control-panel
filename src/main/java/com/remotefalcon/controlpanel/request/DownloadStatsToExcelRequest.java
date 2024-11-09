package com.remotefalcon.controlpanel.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DownloadStatsToExcelRequest {
  private String timezone;
  private Long dateFilterStart;
  private Long dateFilterEnd;
}
