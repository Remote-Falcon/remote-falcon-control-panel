package com.remotefalcon.controlpanel.service;

import java.time.LocalDateTime;
import java.util.*;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.responses.*;
import com.remotefalcon.controlpanel.model.AskWattson;
import com.remotefalcon.controlpanel.repository.NotificationRepository;
import com.remotefalcon.library.documents.Notification;
import com.remotefalcon.library.enums.NotificationType;
import com.remotefalcon.library.models.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.remotefalcon.controlpanel.repository.ShowRepository;
import com.remotefalcon.controlpanel.response.ShowsOnAMap;
import com.remotefalcon.controlpanel.util.AuthUtil;
import com.remotefalcon.controlpanel.util.ClientUtil;
import com.remotefalcon.library.documents.Show;
import com.remotefalcon.library.enums.StatusResponse;
import com.remotefalcon.library.enums.ViewerControlMode;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.remotefalcon.controlpanel.util.WattsonUtil.WATTSON_INSTRUCTIONS;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphQLQueryService {
    private final AuthUtil authUtil;
    private final ClientUtil clientUtil;
    private final ShowRepository showRepository;
    private final NotificationRepository notificationRepository;
    private final HttpServletRequest httpServletRequest;

    @Value("${wattson.key}")
    String wattsonKey;

    @Value("${openai.model:}")
    String openaiModel;
    
    @Value("${wattson.max_output_tokens:0}")
    Long wattsonMaxOutputTokens;

    public Show signIn() {
        String[] basicAuthCredentials = this.authUtil.getBasicAuthCredentials(httpServletRequest);
        if (basicAuthCredentials != null) {
            String ipAddress = this.clientUtil.getClientIp(httpServletRequest);
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
                show.setLastLoginDate(LocalDateTime.now());
                show.setExpireDate(LocalDateTime.now().plusYears(2));
                show.setLastLoginIp(ipAddress);
                this.checkFields(show);
                this.showRepository.save(show);
                show.setServiceToken(this.authUtil.signJwt(show));
                return show;
            }
        }
        throw new RuntimeException(StatusResponse.UNAUTHORIZED.name());
    }

    private void checkFields(Show show) {
        if(show.getPreferences().getViewerControlMode() == null) {
            show.getPreferences().setViewerControlMode(ViewerControlMode.JUKEBOX);
        }
        if(show.getStats() == null) {
            show.setStats(Stat.builder()
                    .jukebox(new ArrayList<>())
                    .page(new ArrayList<>())
                    .voting(new ArrayList<>())
                    .votingWin(new ArrayList<>())
                    .build());
        }
        if(CollectionUtils.isEmpty(show.getRequests())) {
            show.setRequests(new ArrayList<>());
        }
        if(CollectionUtils.isEmpty(show.getVotes())) {
            show.setVotes(new ArrayList<>());
        }
    }

    public Show verifyPasswordResetLink(String passwordResetLink) {
        Optional<Show> show = this.showRepository.findByPasswordResetLinkAndPasswordResetExpiryGreaterThan(passwordResetLink, LocalDateTime.now());
        if(show.isPresent()) {
            String jwt = this.authUtil.signJwt(show.get());
            show.get().setServiceToken(jwt);
            return show.get();
        }
        throw new RuntimeException(StatusResponse.UNAUTHORIZED.name());
    }

    public Show getShow() {
        Optional<Show> show = this.showRepository.findByShowToken(authUtil.tokenDTO.getShowToken());
        if(show.isPresent()) {
            show.get().setLastLoginDate(LocalDateTime.now());
            checkPsaSequences(show.get());
            this.showRepository.save(show.get());

            List<Sequence> sequences = show.get().getSequences();
            sequences.sort(Comparator.comparing(Sequence::getActive)
                            .reversed()
                    .thenComparing(Sequence::getOrder));
            show.get().setSequences(sequences);

            List<Request> jukeboxRequests = show.get().getRequests();
            if(CollectionUtils.isNotEmpty(jukeboxRequests)) {
                jukeboxRequests.sort(Comparator.comparing(Request::getPosition));
            }
            show.get().setRequests(jukeboxRequests);

            return show.get();
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    private void checkPsaSequences(Show show) {
      for(PsaSequence psaSequence : show.getPsaSequences()) {
        if(psaSequence.getLastPlayed() == null) {
          psaSequence.setLastPlayed(LocalDateTime.now());
        }
      }
    }

    public List<ShowsOnAMap> showsOnAMap() {
        List<Show> allShows = this.showRepository.getShowsOnMap();
        List<ShowsOnAMap> showsOnAMapList = new ArrayList<>();
        allShows.forEach(show -> {
            if(show.getPreferences() != null
                    && show.getPreferences().getShowOnMap()) {
                showsOnAMapList.add(ShowsOnAMap.builder()
                                .showName(show.getShowName())
                                .showLatitude(show.getPreferences().getShowLatitude())
                                .showLongitude(show.getPreferences().getShowLongitude())
                        .build());
            }
        });
        return showsOnAMapList;
    }

    public Show getShowByShowSubdomain(String showSubdomain) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
        return show.orElse(null);
    }

    public List<ShowNotification> getNotifications() {
        Optional<Show> show = this.showRepository.findByShowToken(authUtil.tokenDTO.getShowToken());
        if (show.isEmpty()) {
            throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
        }

        Show existingShow = show.get();

        // Source of truth: repository notifications
        List<Notification> repoNotifications = Optional.ofNullable(this.notificationRepository.findAll())
                .orElse(Collections.emptyList());

        // User's current notification states (read/deleted)
        List<ShowNotification> userNotifs = Optional.ofNullable(existingShow.getShowNotifications())
                .orElseGet(ArrayList::new);

        // Index user's notifications by UUID (null-safe)
        Map<String, ShowNotification> userByUuid = new HashMap<>();
        for (ShowNotification sn : userNotifs) {
            if (sn == null) continue;
            Notification n = sn.getNotification();
            String uuid = (n == null) ? null : n.getUuid();
            if (uuid != null) {
                userByUuid.put(uuid, sn);
            }
        }

        List<ShowNotification> merged = new ArrayList<>();

        for (Notification repoNotif : repoNotifications) {
            if (repoNotif == null || repoNotif.getUuid() == null) continue;
            ShowNotification userSN = userByUuid.remove(repoNotif.getUuid());

            if (userSN == null) {
                // New to user → add default state
                merged.add(ShowNotification.builder()
                        .notification(repoNotif)
                        .read(false)
                        .deleted(false)
                        .build());
                continue;
            }

            // Existing in both places: honor user's deleted/read flags
            if (Boolean.TRUE.equals(userSN.getDeleted())) {
                NotificationType type = repoNotif.getType();
                // For ADMIN notifications: keep the deleted record so it is not re-added on next calls
                if (type == NotificationType.ADMIN) {
                    userSN.setNotification(repoNotif);
                    merged.add(userSN); // remains deleted; excluded from returned list but preserved in DB
                }
                // For USER and FPP_HEALTH: drop it completely
                continue;
            }

            // Keep, but update the master Notification details
            userSN.setNotification(repoNotif);
            merged.add(userSN);
        }

        // Add any remaining user-only notifications (repo no longer has them)
        for (ShowNotification leftover : userByUuid.values()) {
            if (leftover == null) continue;
            Notification ln = leftover.getNotification();
            NotificationType lt = ln == null ? null : ln.getType();

            // If an ADMIN notification no longer exists in the repo,
            // delete it completely from the user's notifications (do not add to merged).
            if (lt == NotificationType.ADMIN) {
                continue;
            }

            // For USER and FPP_HEALTH: keep if not deleted
            if (!Boolean.TRUE.equals(leftover.getDeleted())) {
                merged.add(leftover);
            }
        }

        // Cleanup: age-out old FPP_HEALTH notifications and enforce deleted flag removal
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        merged.removeIf(sn -> {
            if (sn == null) return true; // drop null entries defensively
            Notification n = sn.getNotification();
            NotificationType type = (n == null) ? null : n.getType();
            LocalDateTime created = (n == null) ? null : n.getCreatedDate();

            // Remove if user marked deleted: only for USER and FPP_HEALTH
            if (Boolean.TRUE.equals(sn.getDeleted())) {
                return type == NotificationType.USER || type == NotificationType.FPP_HEALTH;
            }

            // Remove stale health notifications
            return type == NotificationType.FPP_HEALTH && created != null && created.isBefore(cutoff);
        });

        // Persist merged state on user
        existingShow.setShowNotifications(merged);
        this.showRepository.save(existingShow);

        // Return non-deleted sorted by createdDate desc (nulls last)
        return merged.stream()
                .filter(sn -> sn != null && !Boolean.TRUE.equals(sn.getDeleted()))
                .sorted(Comparator.comparing(
                        (ShowNotification sn) -> Optional.ofNullable(sn.getNotification())
                                .map(Notification::getCreatedDate)
                                .orElse(null),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public AskWattson askWattson(String prompt, String previousResponseId) {
        Optional<Show> show = this.showRepository.findByShowToken(authUtil.tokenDTO.getShowToken());
        if(show.isEmpty()) {
            return null;
        }
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(wattsonKey)
                .build();

        FileSearchTool fileSearchTool = FileSearchTool.builder()
                .addVectorStoreId("vs_68ac71a920f48191a9e6794324b2ba9b")
                .type(JsonValue.from("file_search"))
                .build();

        String prettyPreferences = this.getPrettyPreferences(show.get().getPreferences());
        String userPreferences = "Here are the show settings specific to this user: " + prettyPreferences;
        String completeInstructions = WATTSON_INSTRUCTIONS + "\n\n" + userPreferences;

        ResponseCreateParams.Builder responseCreateParamsBuilder = ResponseCreateParams.builder()
                .input(prompt)
                .model(ChatModel.of(openaiModel))
                .addTool(fileSearchTool)
                .maxOutputTokens(wattsonMaxOutputTokens)
                .temperature(1.0)
                .topP(1.0)
                .instructions(completeInstructions);

        if(StringUtils.isNotEmpty(previousResponseId)) {
            responseCreateParamsBuilder.previousResponseId(previousResponseId);
            responseCreateParamsBuilder.store(true);
        }

        Response gptResponse = client.responses().create(responseCreateParamsBuilder.build());

        AskWattson.AskWattsonBuilder responseBuilder = AskWattson.builder().responseId(gptResponse.id());

        gptResponse.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .forEach(outputText -> responseBuilder.text(outputText.text()));

        return responseBuilder.build();
    }

    private String getPrettyPreferences(Preference preference) {
        if (preference == null) {
            return "";
        }

        java.util.List<String> pairs = new java.util.ArrayList<>();

        // Top-level preferences (user-friendly labels)
        addPair(pairs, "Viewer Control Enabled", preference.getViewerControlEnabled());
        addPair(pairs, "Viewer Page View Only", preference.getViewerPageViewOnly());
        addPair(pairs, "Viewer Control Mode", preference.getViewerControlMode());
        addPair(pairs, "Jukebox Depth", preference.getJukeboxDepth());
        addPair(pairs, "Jukebox Request Limit", preference.getJukeboxRequestLimit());
        addPair(pairs, "Check If Voted", preference.getCheckIfVoted());
        addPair(pairs, "Check If Requested", preference.getCheckIfRequested());
        addPair(pairs, "PSA Enabled", preference.getPsaEnabled());
        addPair(pairs, "PSA Frequency", preference.getPsaFrequency());
        addPair(pairs, "Location Check Method", preference.getLocationCheckMethod());
        addPair(pairs, "Show Latitude", preference.getShowLatitude());
        addPair(pairs, "Show Longitude", preference.getShowLongitude());
        addPair(pairs, "Allowed Radius", preference.getAllowedRadius());
        addPair(pairs, "Location Code", preference.getLocationCode());
        addPair(pairs, "Hide Sequence Count", preference.getHideSequenceCount());
        addPair(pairs, "Show On Map", preference.getShowOnMap());

        if (preference.getBlockedViewerIps() != null && !preference.getBlockedViewerIps().isEmpty()) {
            addPair(pairs, "Blocked Viewer IPs", String.join("|", preference.getBlockedViewerIps()));
        }

        // Notification preferences (nested)
        NotificationPreference np = preference.getNotificationPreferences();
        if (np != null) {
            addPair(pairs, "Enable FPP Heartbeat", np.getEnableFppHeartbeat());
            addPair(pairs, "FPP Heartbeat If Control Enabled", np.getFppHeartbeatIfControlEnabled());
            addPair(pairs, "FPP Heartbeat Renotify After Minutes", np.getFppHeartbeatRenotifyAfterMinutes());
        }

        return String.join(", ", pairs);
    }

    private void addPair(java.util.List<String> pairs, String key, Object value) {
        if (value == null) return;
        if (value instanceof String s) {
            if (org.apache.commons.lang3.StringUtils.isBlank(s)) return;
            pairs.add(key + "=" + s);
        } else if (value instanceof java.lang.Boolean b) {
            pairs.add(key + "=" + (b ? "true" : "false"));
        } else {
            pairs.add(key + "=" + String.valueOf(value));
        }
    }
}

