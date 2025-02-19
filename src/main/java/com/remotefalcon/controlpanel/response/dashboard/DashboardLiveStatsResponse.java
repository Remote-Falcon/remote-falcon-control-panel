package com.remotefalcon.controlpanel.response.dashboard;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DashboardLiveStatsResponse {
    private Integer currentRequests;
    private Integer totalRequests;
    private Integer currentVotes;
    private Integer totalVotes;
    private String playingNow;
    private String playingNext;
}
