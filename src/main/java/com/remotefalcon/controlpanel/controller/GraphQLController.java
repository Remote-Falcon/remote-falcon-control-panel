package com.remotefalcon.controlpanel.controller;

import com.remotefalcon.controlpanel.aop.RequiresAccess;
import com.remotefalcon.controlpanel.aop.RequiresAdminAccess;
import com.remotefalcon.controlpanel.response.ShowsOnAMap;
import com.remotefalcon.library.documents.Notification;
import com.remotefalcon.library.documents.Show;
import com.remotefalcon.library.models.*;
import com.remotefalcon.controlpanel.response.dashboard.DashboardLiveStatsResponse;
import com.remotefalcon.controlpanel.response.dashboard.DashboardStatsResponse;
import com.remotefalcon.controlpanel.service.DashboardService;
import com.remotefalcon.controlpanel.service.GraphQLMutationService;
import com.remotefalcon.controlpanel.service.GraphQLQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GraphQLController {
    private final GraphQLMutationService graphQLMutationService;
    private final GraphQLQueryService graphQLQueryService;
    private final DashboardService dashboardService;

    /********
    Mutations
     ********/
    @MutationMapping
    public Boolean signUp(@Argument String firstName, @Argument String lastName, @Argument String showName) {
        return graphQLMutationService.signUp(firstName, lastName, showName);
    }

    @MutationMapping
    public Boolean forgotPassword(@Argument String email) {
        return graphQLMutationService.forgotPassword(email);
    }

    @MutationMapping
    public Boolean verifyEmail(@Argument String showToken) {
        return graphQLMutationService.verifyEmail(showToken);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean resetPassword() {
        return this.graphQLMutationService.resetPassword();
    }

    @MutationMapping
    @RequiresAccess
    public Boolean updatePassword() {
        return this.graphQLMutationService.updatePassword();
    }

    @MutationMapping
    @RequiresAccess
    public Boolean updateUserProfile(@Argument UserProfile userProfile) {
        return this.graphQLMutationService.updateUserProfile(userProfile);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean updateExpoPushToken(@Argument String expoPushToken) {
        return this.graphQLMutationService.updateExpoPushToken(expoPushToken);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean requestApiAccess() {
        return this.graphQLMutationService.requestApiAccess();
    }

    @MutationMapping
    @RequiresAccess
    public Boolean deleteAccount() {
        return this.graphQLMutationService.deleteAccount();
    }

    @MutationMapping
    @RequiresAccess
    public Boolean updateShow(@Argument String email, @Argument String showName) {
        return this.graphQLMutationService.updateShow(email, showName);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean updatePreferences(@Argument Preference preferences) {
        return this.graphQLMutationService.updatePreferences(preferences);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean updatePages(@Argument List<ViewerPage> pages) {
        return this.graphQLMutationService.updatePages(pages);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean updatePsaSequences(@Argument List<PsaSequence> psaSequences) {
        return this.graphQLMutationService.updatePsaSequences(psaSequences);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean updateSequences(@Argument List<Sequence> sequences) {
        return this.graphQLMutationService.updateSequences(sequences);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean updateSequenceGroups(@Argument List<SequenceGroup> sequenceGroups) {
        return this.graphQLMutationService.updateSequenceGroups(sequenceGroups);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean playSequenceFromControlPanel(@Argument Sequence sequence) {
        return this.graphQLMutationService.playSequenceFromControlPanel(sequence);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean deleteSingleRequest(@Argument Integer position) {
        return this.graphQLMutationService.deleteSingleRequest(position);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean deleteAllRequests() {
        return this.graphQLMutationService.deleteAllRequests();
    }

    @MutationMapping
    @RequiresAccess
    public Boolean resetAllVotes() {
        return this.graphQLMutationService.resetAllVotes();
    }

    @MutationMapping
    @RequiresAdminAccess
    public Boolean adminUpdateShow(@Argument Show show) {
        return this.graphQLMutationService.adminUpdateShow(show);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean deleteNowPlaying() {
        return this.graphQLMutationService.deleteNowPlaying();
    }

    @MutationMapping
    @RequiresAccess
    public Boolean purgeStats() {
        return this.graphQLMutationService.purgeStats();
    }

    @MutationMapping
    @RequiresAccess
    public Boolean deleteStatsWithinRange(@Argument Long startDate, @Argument Long endDate, @Argument String timezone) {
        return this.graphQLMutationService.deleteStatsWithinRange(startDate, endDate, timezone);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean markNotificationsAsRead(@Argument List<String> uuids) {
        return this.graphQLMutationService.markNotificationsAsRead(uuids);
    }

    @MutationMapping
    @RequiresAccess
    public Boolean deleteNotificationForUser(@Argument String uuid) {
        return this.graphQLMutationService.deleteNotificationForUser(uuid);
    }

    @MutationMapping
    @RequiresAdminAccess
    public Boolean createNotification(@Argument Notification notification) {
        return this.graphQLMutationService.createNotification(notification);
    }

    @MutationMapping
    @RequiresAdminAccess
    public Boolean createNotificationForUser(@Argument Notification notification, @Argument String showSubdomain) {
        return this.graphQLMutationService.createNotificationForUser(notification, showSubdomain);
    }

    @MutationMapping
    @RequiresAdminAccess
    public Boolean deleteNotification(@Argument String uuid) {
        return this.graphQLMutationService.deleteNotification(uuid);
    }


    /*******
     Queries
     *******/
    @QueryMapping
    public Show signIn() {
        return graphQLQueryService.signIn();
    }

    @QueryMapping
    public Show verifyPasswordResetLink(@Argument String passwordResetLink) {
        return graphQLQueryService.verifyPasswordResetLink(passwordResetLink);
    }

    @QueryMapping
    @RequiresAccess()
    public Show getShow() {
        return graphQLQueryService.getShow();
    }

    @QueryMapping
    @RequiresAccess()
    public DashboardStatsResponse dashboardStats(@Argument Long startDate, @Argument Long endDate, @Argument String timezone) {
        return dashboardService.dashboardStats(startDate, endDate, timezone);
    }

    @QueryMapping
    @RequiresAccess()
    public DashboardLiveStatsResponse dashboardLiveStats(@Argument Long startDate, @Argument Long endDate, @Argument String timezone) {
        return dashboardService.dashboardLiveStats(startDate, endDate, timezone);
    }

    @QueryMapping
    @RequiresAccess()
    public List<ShowsOnAMap> showsOnAMap() {
        return graphQLQueryService.showsOnAMap();
    }

    @QueryMapping
    @RequiresAdminAccess
    public Show getShowByShowSubdomain(@Argument String showSubdomain) {
        return graphQLQueryService.getShowByShowSubdomain(showSubdomain);
    }

    @QueryMapping
    @RequiresAccess
    public List<ShowNotification> getNotifications() {
        return this.graphQLQueryService.getNotifications();
    }
}
