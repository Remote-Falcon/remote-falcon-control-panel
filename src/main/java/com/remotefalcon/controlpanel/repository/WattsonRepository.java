package com.remotefalcon.controlpanel.repository;

import com.remotefalcon.library.documents.Wattson;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface WattsonRepository extends MongoRepository<Wattson, String> {
    Wattson findByResponseId(String responseId);
    List<Wattson> findAllByFeedback(String feedback);
}
