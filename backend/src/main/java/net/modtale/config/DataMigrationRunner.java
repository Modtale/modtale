package net.modtale.config;

import net.modtale.model.resources.Mod;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class DataMigrationRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataMigrationRunner.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting legacy data migration checks...");
        migrateOrganizations();
        migrateProjects();
        logger.info("Legacy data migration checks complete.");
    }

    private void migrateOrganizations() {
        Query orgQuery = new Query(Criteria.where("accountType").is("ORGANIZATION"));
        List<User> organizations = mongoTemplate.find(orgQuery, User.class);

        int migratedCount = 0;

        for (User org : organizations) {
            boolean modified = false;

            if (org.getOrganizationRoles() == null || org.getOrganizationRoles().isEmpty()) {
                User.OrganizationRole ownerRole = new User.OrganizationRole(
                        UUID.randomUUID().toString(), "Owner", "#ef4444", EnumSet.allOf(ApiKey.ApiPermission.class)
                );
                ownerRole.setOwner(true);

                User.OrganizationRole adminRole = new User.OrganizationRole(
                        UUID.randomUUID().toString(), "Admin", "#fbbf24", EnumSet.complementOf(EnumSet.of(ApiKey.ApiPermission.ORG_DELETE))
                );

                User.OrganizationRole memberRole = new User.OrganizationRole(
                        UUID.randomUUID().toString(), "Member", "#3b82f6",
                        EnumSet.of(ApiKey.ApiPermission.PROJECT_READ, ApiKey.ApiPermission.VERSION_READ, ApiKey.ApiPermission.VERSION_DOWNLOAD)
                );

                org.setOrganizationRoles(new ArrayList<>(List.of(ownerRole, adminRole, memberRole)));
                modified = true;
            }

            boolean hasOwner = false;
            if (org.getOrganizationMembers() != null) {
                for (User.OrganizationMember member : org.getOrganizationMembers()) {
                    if (member.getRoleId() != null) {
                        User.OrganizationRole role = org.getOrganizationRoles().stream()
                                .filter(r -> r.getId().equals(member.getRoleId())).findFirst().orElse(null);
                        if (role != null && role.isOwner()) hasOwner = true;
                    }

                    if (member.getRoleId() == null && member.getRole() != null) {
                        if ("ADMIN".equalsIgnoreCase(member.getRole())) {
                            if (!hasOwner) {
                                member.setRoleId(org.getOrganizationRoles().get(0).getId());
                                hasOwner = true;
                            } else {
                                member.setRoleId(org.getOrganizationRoles().get(1).getId());
                            }
                        } else {
                            member.setRoleId(org.getOrganizationRoles().get(2).getId());
                        }
                        member.setRole(null);
                        modified = true;
                    }
                }
            }

            if (modified) {
                mongoTemplate.save(org);
                migratedCount++;
            }
        }

        if (migratedCount > 0) {
            logger.info("Successfully migrated {} legacy organizations to the new role system.", migratedCount);
        }
    }

    private void migrateProjects() {
        List<Mod> projects = mongoTemplate.findAll(Mod.class);
        int migratedCount = 0;

        for (Mod project : projects) {
            boolean modified = false;

            if (project.getProjectRoles() == null || project.getProjectRoles().isEmpty()) {
                Mod.ProjectRole adminRole = new Mod.ProjectRole(
                        UUID.randomUUID().toString(), "Admin", "#fbbf24",
                        List.of("PROJECT_EDIT_METADATA", "VERSION_CREATE", "VERSION_EDIT", "VERSION_DELETE", "PROJECT_TEAM_INVITE", "PROJECT_TEAM_REMOVE", "PROJECT_MEMBER_EDIT_ROLE")
                );
                Mod.ProjectRole devRole = new Mod.ProjectRole(
                        UUID.randomUUID().toString(), "Developer", "#3b82f6",
                        List.of("VERSION_CREATE")
                );
                project.setProjectRoles(new ArrayList<>(List.of(adminRole, devRole)));
                modified = true;
            }

            if (project.getContributors() != null && !project.getContributors().isEmpty()) {
                if (project.getTeamMembers() == null) project.setTeamMembers(new ArrayList<>());
                String devRoleId = project.getProjectRoles().get(1).getId(); // Default to Developer

                for (String username : project.getContributors()) {
                    Query userQuery = new Query(Criteria.where("username").regex("^" + Pattern.quote(username) + "$", "i"));
                    User u = mongoTemplate.findOne(userQuery, User.class);

                    if (u != null) {
                        boolean alreadyInTeam = project.getTeamMembers().stream().anyMatch(m -> m.getUserId().equals(u.getId()));
                        if (!alreadyInTeam) {
                            project.getTeamMembers().add(new Mod.ProjectMember(u.getId(), devRoleId));
                        }
                    }
                }
                project.getContributors().clear();
                modified = true;
            }

            if (project.getPendingInvites() != null && !project.getPendingInvites().isEmpty()) {
                if (project.getTeamInvites() == null) project.setTeamInvites(new ArrayList<>());
                String devRoleId = project.getProjectRoles().get(1).getId();

                for (String username : project.getPendingInvites()) {
                    Query userQuery = new Query(Criteria.where("username").regex("^" + Pattern.quote(username) + "$", "i"));
                    User u = mongoTemplate.findOne(userQuery, User.class);

                    if (u != null) {
                        boolean alreadyInvited = project.getTeamInvites().stream().anyMatch(m -> m.getUserId().equals(u.getId()));
                        if (!alreadyInvited) {
                            project.getTeamInvites().add(new Mod.ProjectMember(u.getId(), devRoleId));
                        }
                    }
                }
                project.getPendingInvites().clear();
                modified = true;
            }

            if (modified) {
                mongoTemplate.save(project);
                migratedCount++;
            }
        }

        if (migratedCount > 0) {
            logger.info("Successfully migrated {} legacy projects to the new team system.", migratedCount);
        }
    }
}