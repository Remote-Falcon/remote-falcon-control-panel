package com.remotefalcon.controlpanel.repository;

import com.remotefalcon.library.documents.Show;
import jakarta.transaction.Transactional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShowRepository extends MongoRepository<Show, String> {
    @Transactional
    void deleteByShowToken(String showToken);
    Optional<Show> findByShowToken(String showToken);
    Optional<Show> findByShowSubdomain(String showSubdomain);
    Optional<Show> findByEmailOrShowSubdomain(String email, String showSubdomain);
    Optional<Show> findByEmailIgnoreCase(String email);
    Optional<Show> findByPasswordResetLinkAndPasswordResetExpiryGreaterThan(String passwordResetLink, LocalDateTime passwordResetExpiry);

    @Query("{ 'preferences.showOnMap' : true }")
    List<Show> getShowsOnMap();
}
