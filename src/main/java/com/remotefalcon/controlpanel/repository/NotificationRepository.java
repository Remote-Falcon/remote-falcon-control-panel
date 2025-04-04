package com.remotefalcon.controlpanel.repository;

import com.remotefalcon.library.documents.Notification;
import jakarta.transaction.Transactional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    @Transactional
    void deleteByUuid(String uuid);
}
