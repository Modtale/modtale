package net.modtale.service.analytics;

import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsEligibilityService {

    private final UserRepository userRepository;

    public AnalyticsEligibilityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean shouldCountProjectEngagement(Project project, User viewer) {
        if (project == null) {
            return false;
        }
        return !isAffiliatedWithProject(project, viewer);
    }

    private boolean isAffiliatedWithProject(Project project, User viewer) {
        if (viewer == null || viewer.getId() == null) {
            return false;
        }

        if (viewer.getId().equals(project.getAuthorId())) {
            return true;
        }

        if (project.getTeamMembers() != null && project.getTeamMembers().stream()
                .anyMatch(member -> viewer.getId().equals(member.getUserId()))) {
            return true;
        }

        if (project.getAuthorId() == null) {
            return false;
        }

        User author = userRepository.findById(project.getAuthorId())
                .filter(candidate -> !candidate.isDeleted())
                .orElse(null);
        if (author == null || author.getAccountType() != User.AccountType.ORGANIZATION) {
            return false;
        }

        return author.getOrganizationMembers() != null && author.getOrganizationMembers().stream()
                .anyMatch(member -> viewer.getId().equals(member.getUserId()));
    }
}
