package com.remotefalcon.controlpanel.service;

import java.time.LocalDateTime;
import java.util.*;

import com.remotefalcon.controlpanel.model.AskWattson;
import com.remotefalcon.controlpanel.repository.NotificationRepository;
import com.remotefalcon.library.documents.Notification;
import com.remotefalcon.library.enums.NotificationType;
import com.remotefalcon.library.models.*;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphQLQueryService {
    private final AuthUtil authUtil;
    private final ClientUtil clientUtil;
    private final ShowRepository showRepository;
    private final NotificationRepository notificationRepository;
    private final HttpServletRequest httpServletRequest;

    @Value("${wattson.endpoint}")
    String wattsonEndpoint;

    @Value("${wattson.key}")
    String wattsonKey;

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
        if(show.isPresent()) {
            Show existingShow = show.get();
            List<Notification> notifications = this.notificationRepository.findAll();

            List<ShowNotification> showNotifications = existingShow.getShowNotifications() == null ? new ArrayList<>() : existingShow.getShowNotifications();
            notifications.forEach(notification -> {
                if(showNotifications.stream().noneMatch(showNotification -> showNotification.getNotification().getUuid().equals(notification.getUuid()))) {
                    showNotifications.add(ShowNotification.builder()
                            .notification(notification)
                            .read(false)
                            .deleted(false)
                            .build());
                }
            });

            showNotifications.removeIf(showNotification -> showNotification.getNotification().getType().equals(NotificationType.FPP_HEALTH)
                    && showNotification.getNotification().getCreatedDate().isBefore(LocalDateTime.now().minusDays(1)));

            showNotifications.removeIf(showNotification -> showNotification.getNotification().getType().equals(NotificationType.USER) && showNotification.getDeleted());

            existingShow.setShowNotifications(showNotifications);

            this.showRepository.save(existingShow);

            return showNotifications.stream()
                    .filter(showNotification -> !showNotification.getDeleted())
                    .sorted(Comparator.comparing(showNotification -> showNotification.getNotification().getCreatedDate(),
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    public AskWattson askWattson(String prompt) {
        Optional<Show> show = this.showRepository.findByShowToken(authUtil.tokenDTO.getShowToken());
        if(show.isPresent()) {
            Show existingShow = show.get();

            if(existingShow.getUserProfile() == null) {
                throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
            }

            int totalTokens = 0;
            LocalDateTime lastReset = existingShow.getUserProfile().getLastTokenResetDate();
            if (lastReset == null || lastReset.isBefore(LocalDateTime.now().minusMonths(1))) {
                existingShow.getUserProfile().setTotalTokens(totalTokens);
                existingShow.getUserProfile().setLastTokenResetDate(LocalDateTime.now());
            }
            totalTokens = existingShow.getUserProfile().getTotalTokens() == null ? 0 : existingShow.getUserProfile().getTotalTokens();
            if (totalTokens >= 250_000) {
                throw new RuntimeException("TOKEN_LIMIT_EXCEEDED");
            }

            return callWattson(prompt, existingShow);
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    private AskWattson callWattson(String prompt, Show show) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + wattsonKey);
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

            prompt = (prompt == null) ? "" : prompt;
            Map<String, Object> body = new HashMap<>();
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);
            body.put("messages", messages);
            body.put("stream", false);
            body.put("include_functions_info", false);
            body.put("include_retrieval_info", false);
            body.put("include_guardrails_info", false);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<AskWattson> response = restTemplate.postForEntity(wattsonEndpoint + "/api/v1/chat/completions", entity, AskWattson.class);

            if(response.getBody() != null && response.getBody().getUsage() != null) {
                Integer totalTokensUsed = response.getBody().getUsage().getTotal_tokens();
                show.getUserProfile().setTotalTokens(show.getUserProfile().getTotalTokens() + totalTokensUsed);
                this.showRepository.save(show);
            }

            return response.getBody();
        } catch (Exception e) {
            log.error("Error calling Wattson endpoint", e);
            throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
        }
    }
}
