package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import net.modtale.model.dto.request.finance.StageAdSettlementRequest;
import net.modtale.model.project.Project;
import net.modtale.model.user.AdminPermission;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

class AdSettlementStagingTest {
    @Test void financeReviewPermissionDoesNotGrantCreatorPolicyOwnership() {
        var reviewer = new User(); reviewer.setId("reviewer"); reviewer.setAdminPermissions(Set.of(AdminPermission.PLATFORM_FINANCE_MANAGE));
        var owner = new User(); owner.setId("owner"); var project = new Project(); project.setAuthorId("owner");
        assertDoesNotThrow(() -> AdSettlementStagingService.requireReviewer(reviewer));
        assertThrows(SecurityException.class, () -> AdSettlementStagingService.requireReviewer(owner));
        assertThrows(SecurityException.class, () -> AdSettlementStagingService.requireReviewer(null));
        var core = new RevenueOpsSupport(); var users = mock(UserRepository.class);
        when(users.findById("owner")).thenReturn(java.util.Optional.of(owner));
        ReflectionTestUtils.setField(core, "userRepository", users);
        assertDoesNotThrow(() -> core.requireProjectMonetizationOwner(owner, project));
        assertThrows(SecurityException.class, () -> core.requireProjectMonetizationOwner(reviewer, project));
        var organization = new User(); organization.setAccountType(User.AccountType.ORGANIZATION);
        assertThrows(SecurityException.class, () -> core.requireOrganizationOwner(reviewer, organization));
        var member = new User.OrganizationMember("reviewer", "editor"); member.setRole("OWNER");
        organization.setOrganizationMembers(List.of(member));
        organization.setOrganizationRoles(List.of(new User.OrganizationRole("editor", "Editor", "blue", Set.of())));
        assertThrows(SecurityException.class, () -> core.requireOrganizationOwner(reviewer, organization));
        var ownerRole = new User.OrganizationRole("owner", "Owner", "blue", Set.of()); ownerRole.setOwner(true);
        organization.setOrganizationRoles(List.of(ownerRole)); member.setRoleId("owner");
        assertDoesNotThrow(() -> core.requireOrganizationOwner(reviewer, organization));
    }
    @Test void reportValidationRequiresClosedDatesAndNonSecretIdentities() {
        var valid = new StageAdSettlementRequest("provider", "publisher-1", "report-1", "deposit-1", "usd", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 10001, "a".repeat(64));
        assertDoesNotThrow(() -> AdSettlementStagingService.validate(valid));
        assertEquals(AdSettlementStagingService.digest(valid), AdSettlementStagingService.digest(valid));
        assertThrows(IllegalArgumentException.class, () -> AdSettlementStagingService.validate(new StageAdSettlementRequest("https://provider.example", "x", "r", "d", "usd", valid.from(), valid.through(), 100, valid.reportSha256())));
        assertThrows(IllegalArgumentException.class, () -> AdSettlementStagingService.validate(new StageAdSettlementRequest("provider", "x", "r", "d", "usd", valid.from(), LocalDate.now().plusDays(1), 100, valid.reportSha256())));
    }
    @Test void activityExcludesClicksGenericApiDownloadsAndUncertainHistoricOwners() {
        var mongo = mock(MongoTemplate.class); var projects = mock(ProjectRepository.class); var users = mock(UserRepository.class);
        var service = new AdSettlementActivityService(mongo, projects, users);
        var owner = new User(); owner.setId("owner");
        var eligible = new Project(); eligible.setId("eligible"); eligible.setAuthorId("owner"); eligible.setAdsEnabled(true);
        var legacy = new Project(); legacy.setId("legacy"); legacy.setAuthorId("owner"); legacy.setAdsEnabled(true);
        when(projects.findAllById(any())).thenReturn(List.of(eligible, legacy)); when(users.findAllById(any())).thenReturn(List.of(owner));
        var counts = new Document("v", 10).append("l", 3).append("a", 9999).append("f", 500).append("clicks", 9000);
        when(mongo.find(any(Query.class), eq(Document.class), eq("project_monthly_stats"))).thenReturn(List.of(
                new Document("projectId", "eligible").append("authorId", "owner").append("year", 2026).append("month", 8).append("days", new Document("2", counts)),
                new Document("projectId", "legacy").append("authorId", "display-name").append("year", 2026).append("month", 8).append("days", new Document("2", counts))));
        var rows = service.snapshot(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertEquals(13, rows.getFirst().provisionalPoints());
        assertEquals(0, rows.getLast().provisionalPoints());
        assertTrue(rows.getLast().eligibilityNote().contains("historical owner"));
        assertTrue(rows.stream().allMatch(row -> row.provisionalCreatorCents() == 0));
    }
}
