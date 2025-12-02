package com.remotefalcon.controlpanel.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import com.remotefalcon.library.models.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.remotefalcon.controlpanel.dto.TokenDTO;
import com.remotefalcon.controlpanel.repository.ShowRepository;
import com.remotefalcon.controlpanel.request.DownloadStatsToExcelRequest;
import com.remotefalcon.controlpanel.response.dashboard.DashboardLiveStatsResponse;
import com.remotefalcon.controlpanel.response.dashboard.DashboardStatsResponse;
import com.remotefalcon.controlpanel.util.AuthUtil;
import com.remotefalcon.controlpanel.util.ExcelUtil;
import com.remotefalcon.library.documents.Show;
import com.remotefalcon.library.enums.StatusResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
  private final AuthUtil jwtUtil;
  private final ExcelUtil excelUtil;
  private final ShowRepository showRepository;

  public DashboardStatsResponse dashboardStats(Long startDate, Long endDate, String timezone) {
    TokenDTO tokenDTO = this.jwtUtil.getJwtPayload();
    Optional<Show> show = this.showRepository.findByShowToken(tokenDTO.getShowToken());
    if(show.isEmpty()) {
      throw new RuntimeException(StatusResponse.SHOW_NOT_FOUND.name());
    }

    ZonedDateTime startDateAtZone = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startDate), ZoneId.of(timezone));
    ZonedDateTime endDateAtZone = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endDate), ZoneId.of(timezone)).plusDays(2);

    List<DashboardStatsResponse.Stat> pageStats = this.buildPageStats(startDateAtZone, endDateAtZone, timezone, show.get());
    List<DashboardStatsResponse.Stat> jukeboxStatsByDate = this.buildJukeboxStatsByDate(startDateAtZone, endDateAtZone, timezone, show.get());
    DashboardStatsResponse.Stat jukeboxStatsBySequence = this.buildJukeboxStatsBySequence(startDateAtZone, endDateAtZone, timezone, show.get());
    List<DashboardStatsResponse.Stat> voteStatsByDate = this.buildVoteStatsByDate(startDateAtZone, endDateAtZone, timezone, show.get());
    DashboardStatsResponse.Stat voteStatsBySequence = this.buildVoteStatsBySequence(startDateAtZone, endDateAtZone, timezone, show.get());
    List<DashboardStatsResponse.Stat> voteWinStatsByDate = this.buildVoteWinStatsByDate(startDateAtZone, endDateAtZone, timezone, show.get());
    DashboardStatsResponse.Stat voteWinStatsBySequence = this.buildVoteWinStatsBySequence(startDateAtZone, endDateAtZone, timezone, show.get());

    return DashboardStatsResponse.builder()
            .page(pageStats)
            .jukeboxByDate(jukeboxStatsByDate)
            .jukeboxBySequence(jukeboxStatsBySequence)
            .votingByDate(voteStatsByDate)
            .votingBySequence(voteStatsBySequence)
            .votingWinByDate(voteWinStatsByDate)
            .votingWinBySequence(voteWinStatsBySequence)
            .build();
  }

  public DashboardLiveStatsResponse dashboardLiveStats(Long startDate, Long endDate, String timezone) {
    TokenDTO tokenDTO = this.jwtUtil.getJwtPayload();
    Optional<Show> show = this.showRepository.findByShowToken(tokenDTO.getShowToken());
    if(show.isEmpty()) {
      throw new RuntimeException(StatusResponse.SHOW_NOT_FOUND.name());
    }

    Show existingShow = show.get();
    ZoneId userZone = ZoneId.of(timezone);
    // Only return stats for the current day in the user's timezone.
    ZonedDateTime startDateAtZone = ZonedDateTime.now(userZone).toLocalDate().atStartOfDay(userZone);
    ZonedDateTime endDateAtZone = startDateAtZone.plusDays(1);

    return DashboardLiveStatsResponse.builder()
            .currentRequests(show.get().getRequests() != null ? show.get().getRequests().size() : 0)
            .totalRequests(this.buildTotalRequestsLiveStat(startDateAtZone, endDateAtZone, timezone, show.get(), false))
            .currentVotes(show.get().getVotes() != null ? show.get().getVotes().stream().mapToInt(Vote::getVotes).sum() : 0)
            .totalVotes(this.buildTotalVotesLiveStat(startDateAtZone, endDateAtZone, timezone, show.get(), false))
            .playingNow(getPlayingNow(existingShow))
            .playingNext(getPlayingNext(existingShow))
            .build();
  }

  private String getPlayingNow(Show show) {
    Optional<Sequence> playingNowSequence = show.getSequences().stream()
            .filter(sequence -> StringUtils.equalsIgnoreCase(sequence.getName(), show.getPlayingNow()))
            .findFirst();
    return playingNowSequence.map(Sequence::getDisplayName).orElse(show.getPlayingNow());
  }

  private String getPlayingNext(Show show) {
    Optional<Request> nextRequest = show.getRequests().stream()
            .min(Comparator.comparing(Request::getPosition));

    if(nextRequest.isPresent()) {
      return nextRequest.get().getSequence().getDisplayName();
    }else {
      Optional<Sequence> playingNextScheduledSequence = show.getSequences().stream()
              .filter(sequence -> StringUtils.equalsIgnoreCase(sequence.getName(), show.getPlayingNextFromSchedule()))
              .findFirst();
      return playingNextScheduledSequence.map(Sequence::getDisplayName).orElse(show.getPlayingNextFromSchedule());
    }
  }

  public ResponseEntity<ByteArrayResource> downloadStatsToExcel(DownloadStatsToExcelRequest downloadStatsToExcelRequest) {
    DashboardStatsResponse dashboardStats = this.dashboardStats(downloadStatsToExcelRequest.getDateFilterStart(), downloadStatsToExcelRequest.getDateFilterEnd(), downloadStatsToExcelRequest.getTimezone());
    if(dashboardStats != null) {
      return excelUtil.generateDashboardExcel(dashboardStats, downloadStatsToExcelRequest.getTimezone());
    }
    return ResponseEntity.status(204).build();
  }

  private List<DashboardStatsResponse.Stat> buildPageStats(ZonedDateTime startDateAtZone, ZonedDateTime endDateAtZone, String timezone, Show show) {
    List<DashboardStatsResponse.Stat> pageStats = new ArrayList<>();
    if(show.getStats() == null) {
      return pageStats;
    }
    ZoneId userZone = ZoneId.of(timezone);
    Map<LocalDate, List<Stat.Page>> pageStatsGroupedByDate = show.getStats().getPage()
            .stream()
            .map(stat -> Map.entry(stat, convertStatDateTime(stat.getDateTime(), userZone)))
            .filter(stat -> stat.getValue().isAfter(startDateAtZone))
            .filter(stat -> stat.getValue().isBefore(endDateAtZone))
            .filter(stat -> stat.getKey().getIp() != null)
            .collect(Collectors.groupingBy(stat -> stat.getValue().toLocalDate(), Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    this.fillStatDateGaps(startDateAtZone, endDateAtZone, pageStatsGroupedByDate);

    pageStatsGroupedByDate.forEach((date, stat) -> pageStats.add(DashboardStatsResponse.Stat.builder()
            .date(ZonedDateTime.of(date, date.atStartOfDay().toLocalTime(), userZone).toInstant().toEpochMilli())
            .total(stat.size())
            .unique(stat.stream().collect(Collectors.groupingBy(Stat.Page::getIp)).size())
            .viewerIps(stat.stream().map(Stat.Page::getIp).collect(Collectors.toSet()))
            .build()));

    pageStats.sort(Comparator.comparing(DashboardStatsResponse.Stat::getDate));

    return pageStats;
  }

  private List<DashboardStatsResponse.Stat> buildJukeboxStatsByDate(ZonedDateTime startDateAtZone, ZonedDateTime endDateAtZone, String timezone, Show show) {
    List<DashboardStatsResponse.Stat> jukeboxStats = new ArrayList<>();
    if(show.getStats() == null) {
      return jukeboxStats;
    }
    ZoneId userZone = ZoneId.of(timezone);
    Map<LocalDate, List<Stat.Jukebox>> jukeboxStatsGroupedByDate = show.getStats().getJukebox()
            .stream()
            .map(stat -> Map.entry(stat, convertStatDateTime(stat.getDateTime(), userZone)))
            .filter(stat -> stat.getValue().isAfter(startDateAtZone))
            .filter(stat -> stat.getValue().isBefore(endDateAtZone))
            .collect(Collectors.groupingBy(stat -> stat.getValue().toLocalDate(), Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    this.fillStatDateGaps(startDateAtZone, endDateAtZone, jukeboxStatsGroupedByDate);

    jukeboxStatsGroupedByDate.forEach((date, stat) -> {
      List<DashboardStatsResponse.SequenceStat> sequences = new ArrayList<>();
      Map<String, List<Stat.Jukebox>> requests = stat.stream()
              .collect(Collectors.groupingBy(Stat.Jukebox::getName));
      requests.forEach((sequence, request) -> sequences.add(DashboardStatsResponse.SequenceStat.builder()
              .total(request.size())
              .name(sequence)
              .build()));
      jukeboxStats.add(DashboardStatsResponse.Stat.builder()
              .sequences(sequences.stream()
                      .sorted(Comparator.comparing(DashboardStatsResponse.SequenceStat::getTotal).reversed())
                      .toList())
              .date(ZonedDateTime.of(date, date.atStartOfDay().toLocalTime(), userZone).toInstant().toEpochMilli())
              .total(stat.size())
              .build());
    });

    jukeboxStats.sort(Comparator.comparing(DashboardStatsResponse.Stat::getDate));

    return jukeboxStats;
  }

  private DashboardStatsResponse.Stat buildJukeboxStatsBySequence(ZonedDateTime startDateAtZone, ZonedDateTime endDateAtZone, String timezone, Show show) {
    List<DashboardStatsResponse.SequenceStat> sequences = new ArrayList<>();
    if(show.getStats() == null) {
      return DashboardStatsResponse.Stat.builder()
              .sequences(sequences)
              .build();
    }
    ZoneId userZone = ZoneId.of(timezone);
    Map<String, List<Stat.Jukebox>> jukeboxStatsGroupedBySequence = show.getStats().getJukebox()
            .stream()
            .map(stat -> Map.entry(stat, convertStatDateTime(stat.getDateTime(), userZone)))
            .filter(stat -> stat.getValue().isAfter(startDateAtZone))
            .filter(stat -> stat.getValue().isBefore(endDateAtZone))
            .collect(Collectors.groupingBy(stat -> stat.getKey().getName(), Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    jukeboxStatsGroupedBySequence.forEach((sequence, stat) -> sequences.add(DashboardStatsResponse.SequenceStat.builder()
            .total(stat.size())
            .name(sequence)
            .build()));

    sequences.sort(Comparator.comparing(DashboardStatsResponse.SequenceStat::getTotal).reversed());

    return DashboardStatsResponse.Stat.builder()
            .sequences(sequences)
            .build();
  }

  private List<DashboardStatsResponse.Stat> buildVoteStatsByDate(ZonedDateTime startDateAtZone, ZonedDateTime endDateAtZone, String timezone, Show show) {
    List<DashboardStatsResponse.Stat> votingStats = new ArrayList<>();
    if(show.getStats() == null) {
      return votingStats;
    }
    ZoneId userZone = ZoneId.of(timezone);
    Map<LocalDate, List<Stat.Voting>> votingStatsGroupedByDate = show.getStats().getVoting()
            .stream()
            .map(stat -> Map.entry(stat, convertStatDateTime(stat.getDateTime(), userZone)))
            .filter(stat -> stat.getValue().isAfter(startDateAtZone))
            .filter(stat -> stat.getValue().isBefore(endDateAtZone))
            .collect(Collectors.groupingBy(stat -> stat.getValue().toLocalDate(), Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    this.fillStatDateGaps(startDateAtZone, endDateAtZone, votingStatsGroupedByDate);

    votingStatsGroupedByDate.forEach((date, stat) -> {
      List<DashboardStatsResponse.SequenceStat> sequences = new ArrayList<>();
      Map<String, List<Stat.Voting>> votes = stat.stream()
              .collect(Collectors.groupingBy(Stat.Voting::getName));
      votes.forEach((sequence, vote) -> sequences.add(DashboardStatsResponse.SequenceStat.builder()
              .total(vote.size())
              .name(sequence)
              .build()));
      votingStats.add(DashboardStatsResponse.Stat.builder()
              .sequences(sequences.stream()
                      .sorted(Comparator.comparing(DashboardStatsResponse.SequenceStat::getTotal).reversed())
                      .toList())
              .date(ZonedDateTime.of(date, date.atStartOfDay().toLocalTime(), userZone).toInstant().toEpochMilli())
              .total(stat.size())
              .build());
    });

    votingStats.sort(Comparator.comparing(DashboardStatsResponse.Stat::getDate));

    return votingStats;
  }

  private DashboardStatsResponse.Stat buildVoteStatsBySequence(ZonedDateTime startDateAtZone, ZonedDateTime endDateAtZone, String timezone, Show show) {
    List<DashboardStatsResponse.SequenceStat> sequences = new ArrayList<>();
    if(show.getStats() == null) {
      return DashboardStatsResponse.Stat.builder()
              .sequences(sequences)
              .build();
    }
    ZoneId userZone = ZoneId.of(timezone);
    Map<String, List<Stat.Voting>> voteStatsGroupedBySequence = show.getStats().getVoting()
            .stream()
            .map(stat -> Map.entry(stat, convertStatDateTime(stat.getDateTime(), userZone)))
            .filter(stat -> stat.getValue().isAfter(startDateAtZone))
            .filter(stat -> stat.getValue().isBefore(endDateAtZone))
            .collect(Collectors.groupingBy(stat -> stat.getKey().getName(), Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    voteStatsGroupedBySequence.forEach((sequence, stat) -> sequences.add(DashboardStatsResponse.SequenceStat.builder()
            .total(stat.size())
            .name(sequence)
            .build()));

    sequences.sort(Comparator.comparing(DashboardStatsResponse.SequenceStat::getTotal).reversed());

    return DashboardStatsResponse.Stat.builder()
            .sequences(sequences)
            .build();
  }

  private List<DashboardStatsResponse.Stat> buildVoteWinStatsByDate(ZonedDateTime startDateAtZone, ZonedDateTime endDateAtZone, String timezone, Show show) {
    List<DashboardStatsResponse.Stat> votingWinStats = new ArrayList<>();
    if(show.getStats() == null) {
      return votingWinStats;
    }
    ZoneId userZone = ZoneId.of(timezone);
    Map<LocalDate, List<Stat.VotingWin>> votingWinStatsGroupedByDate = show.getStats().getVotingWin()
            .stream()
            .map(stat -> Map.entry(stat, convertStatDateTime(stat.getDateTime(), userZone)))
            .sorted(Comparator.comparing(entry -> entry.getValue().toLocalDateTime()))
            .filter(stat -> stat.getValue().isAfter(startDateAtZone))
            .filter(stat -> stat.getValue().isBefore(endDateAtZone))
            .collect(Collectors.groupingBy(stat -> stat.getValue().toLocalDate(), Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    this.fillStatDateGaps(startDateAtZone, endDateAtZone, votingWinStatsGroupedByDate);

    votingWinStatsGroupedByDate.forEach((date, stat) -> {
      List<DashboardStatsResponse.SequenceStat> sequences = new ArrayList<>();
      Map<String, List<Stat.VotingWin>> voteWins = stat.stream()
              .collect(Collectors.groupingBy(Stat.VotingWin::getName));
      voteWins.forEach((sequence, win) -> sequences.add(DashboardStatsResponse.SequenceStat.builder()
              .total(win.size())
              .name(sequence)
              .build()));
      votingWinStats.add(DashboardStatsResponse.Stat.builder()
              .sequences(sequences.stream()
                      .sorted(Comparator.comparing(DashboardStatsResponse.SequenceStat::getTotal).reversed())
                      .toList())
              .date(ZonedDateTime.of(date, date.atStartOfDay().toLocalTime(), userZone).toInstant().toEpochMilli())
              .total(stat.size())
              .build());
    });

    votingWinStats.sort(Comparator.comparing(DashboardStatsResponse.Stat::getDate));

    return votingWinStats;
  }

  private DashboardStatsResponse.Stat buildVoteWinStatsBySequence(ZonedDateTime startDateAtZone, ZonedDateTime endDateAtZone, String timezone, Show show) {
    List<DashboardStatsResponse.SequenceStat> sequences = new ArrayList<>();
    if(show.getStats() == null) {
      return DashboardStatsResponse.Stat.builder()
              .sequences(sequences)
              .build();
    }
    ZoneId userZone = ZoneId.of(timezone);
    Map<String, List<Stat.VotingWin>> voteWinStatsGroupedBySequence = show.getStats().getVotingWin()
            .stream()
            .map(stat -> Map.entry(stat, convertStatDateTime(stat.getDateTime(), userZone)))
            .filter(stat -> stat.getValue().isAfter(startDateAtZone))
            .filter(stat -> stat.getValue().isBefore(endDateAtZone))
            .collect(Collectors.groupingBy(stat -> stat.getKey().getName(), Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    voteWinStatsGroupedBySequence.forEach((sequence, stat) -> sequences.add(DashboardStatsResponse.SequenceStat.builder()
            .total(stat.size())
            .name(sequence)
            .build()));

    sequences.sort(Comparator.comparing(DashboardStatsResponse.SequenceStat::getTotal).reversed());

    return DashboardStatsResponse.Stat.builder()
            .sequences(sequences)
            .build();
  }

  private Integer buildTotalRequestsLiveStat(ZonedDateTime startDateAtZone, ZonedDateTime endDateAtZone, String timezone, Show show, Boolean fillDays) {
    List<DashboardStatsResponse.Stat> jukeboxStats = new ArrayList<>();
    if(show.getStats() == null) {
      return 0;
    }
    ZoneId userZone = ZoneId.of(timezone);
    Map<LocalDate, List<Stat.Jukebox>> jukeboxStatsGroupedByDate = show.getStats().getJukebox()
            .stream()
            .map(stat -> Map.entry(stat, convertStatDateTime(stat.getDateTime(), userZone)))
            .filter(stat -> stat.getValue().isAfter(startDateAtZone))
            .filter(stat -> stat.getValue().isBefore(endDateAtZone))
            .collect(Collectors.groupingBy(stat -> stat.getValue().toLocalDate(), Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    if(fillDays) {
      this.fillStatDateGaps(startDateAtZone, endDateAtZone, jukeboxStatsGroupedByDate);
    }

    jukeboxStatsGroupedByDate.forEach((date, stat) -> jukeboxStats.add(DashboardStatsResponse.Stat.builder()
            .date(ZonedDateTime.of(date, date.atStartOfDay().toLocalTime(), userZone).toInstant().toEpochMilli())
            .total(stat.size())
            .unique(stat.stream().collect(Collectors.groupingBy(Stat.Jukebox::getName)).size())
            .build()));

    return jukeboxStats.stream()
            .mapToInt(DashboardStatsResponse.Stat::getTotal)
            .sum();
  }

  private Integer buildTotalVotesLiveStat(ZonedDateTime startDateAtZone, ZonedDateTime endDateAtZone, String timezone, Show show, Boolean fillDays) {
    List<DashboardStatsResponse.Stat> voteStats = new ArrayList<>();
    if(show.getStats() == null) {
      return 0;
    }
    ZoneId userZone = ZoneId.of(timezone);
    Map<LocalDate, List<Stat.Voting>> voteStatsGroupedByDate = show.getStats().getVoting()
            .stream()
            .map(stat -> Map.entry(stat, convertStatDateTime(stat.getDateTime(), userZone)))
            .filter(stat -> stat.getValue().isAfter(startDateAtZone))
            .filter(stat -> stat.getValue().isBefore(endDateAtZone))
            .collect(Collectors.groupingBy(stat -> stat.getValue().toLocalDate(), Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    if(fillDays) {
      this.fillStatDateGaps(startDateAtZone, endDateAtZone, voteStatsGroupedByDate);
    }

    voteStatsGroupedByDate.forEach((date, stat) -> voteStats.add(DashboardStatsResponse.Stat.builder()
            .date(ZonedDateTime.of(date, date.atStartOfDay().toLocalTime(), userZone).toInstant().toEpochMilli())
            .total(stat.size())
            .unique(stat.stream().collect(Collectors.groupingBy(Stat.Voting::getName)).size())
            .build()));

    return voteStats.stream()
            .mapToInt(DashboardStatsResponse.Stat::getTotal)
            .sum();
  }

  @SuppressWarnings("unchecked")
  private <V> void fillStatDateGaps(ZonedDateTime startDateAtZone, ZonedDateTime endDateAtZone, Map<LocalDate, V> statMap) {
    List<LocalDate> datesInRange = startDateAtZone.toLocalDate().datesUntil(endDateAtZone.toLocalDate()).toList();
    datesInRange.forEach(date -> {
      if(!statMap.containsKey(date)) {
        statMap.put(date, (V) new ArrayList<>());
      }
    });
  }

  private ZonedDateTime convertStatDateTime(LocalDateTime statDateTime, ZoneId userZone) {
    return statDateTime.atZone(ZoneOffset.UTC).withZoneSameInstant(userZone);
  }
}
