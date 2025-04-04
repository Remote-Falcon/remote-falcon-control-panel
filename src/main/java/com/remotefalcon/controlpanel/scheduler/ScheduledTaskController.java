package com.remotefalcon.controlpanel.scheduler;

import com.remotefalcon.controlpanel.service.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduledTaskController {
    private final ScheduledTaskService scheduledTaskService;

    @Scheduled(cron = "0 * * * * *")
    public void runTask() {
        scheduledTaskService.fppHeartbeatTask();
    }
}
