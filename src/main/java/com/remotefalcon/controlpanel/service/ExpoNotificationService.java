package com.remotefalcon.controlpanel.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.remotefalcon.controlpanel.request.ExpoPushRequest;

@Service
@Slf4j
public class ExpoNotificationService {

    @Value("${expo-push-url}")
    String expoPushUrl;

    public void sendExpoPush(String to, String title, String body) {
        ExpoPushRequest expoPushRequest = ExpoPushRequest.builder()
            .to(to)
            .title(title)
            .body(body)
            .sound("default")
            .build();

        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.postForEntity(expoPushUrl, expoPushRequest, Object.class);
        }catch (Exception e) {
            //log.error("Unable to send expo push notification for {}", to);
        }
    }
}
