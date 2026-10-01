package net.modtale.service.jam;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.Modjam.JamPermission;
import net.modtale.model.user.User;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.user.UserRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModjamOrganizerService {
    private final ModjamRepository jams;
    private final UserRepository users;
    private final MongoTemplate mongo;

    public ModjamOrganizerService(ModjamRepository jams, UserRepository users, MongoTemplate mongo) {
        this.jams = jams;
        this.users = users;
        this.mongo = mongo;
    }

    public boolean permits(Modjam jam, String userId, JamPermission permission) {
        if (userId == null) return false;
        if (Objects.equals(jam.getHostId(), userId)) return true;
        if (jam.getOrganizerMembers() == null || jam.getOrganizerRoles() == null) return false;
        return jam.getOrganizerMembers().stream().filter(Objects::nonNull)
                .filter(member -> userId.equals(member.userId()))
                .anyMatch(member -> jam.getOrganizerRoles().stream().filter(Objects::nonNull)
                        .anyMatch(role -> Objects.equals(role.id(), member.roleId())
                                && role.permissions() != null && role.permissions().contains(permission)));
    }

    public boolean isOrganizer(Modjam jam, String userId) {
        return userId != null && (Objects.equals(jam.getHostId(), userId)
                || jam.getOrganizerMembers() != null && jam.getOrganizerMembers().stream().filter(Objects::nonNull)
                .anyMatch(member -> userId.equals(member.userId())));
    }

    public void require(Modjam jam, String userId, JamPermission permission) {
        if (!permits(jam, userId, permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your organizer role does not allow this action.");
        }
    }

    public Modjam saveRole(String jamId, String userId, Modjam.OrganizerRole input) {
        Modjam jam = owned(jamId, userId);
        if (input == null || input.name() == null || input.name().isBlank() || input.name().length() > 50
                || input.color() == null || !input.color().matches("^#[a-fA-F0-9]{6}$")
                || input.permissions() == null || input.permissions().isEmpty() || input.permissions().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Roles need a name, a six-digit color and at least one permission.");
        }
        List<Modjam.OrganizerRole> roles = mutable(jam.getOrganizerRoles());
        String id = input.id() == null || input.id().isBlank() ? UUID.randomUUID().toString() : input.id();
        if (input.id() != null && !input.id().isBlank() && roles.stream().noneMatch(role -> id.equals(role.id()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizer role not found.");
        }
        roles.removeIf(role -> id.equals(role.id()));
        if (roles.size() >= 20) throw new IllegalArgumentException("A jam supports up to 20 organizer roles.");
        roles.add(new Modjam.OrganizerRole(id, input.name().trim(), input.color(), Set.copyOf(input.permissions())));
        jam.setOrganizerRoles(roles);
        persist(jam, "organizerRoles", roles);
        return jam;
    }

    public Modjam deleteRole(String jamId, String userId, String roleId) {
        Modjam jam = owned(jamId, userId);
        if (jam.getOrganizerMembers() != null && jam.getOrganizerMembers().stream().anyMatch(member -> roleId.equals(member.roleId()))
                || jam.getPendingOrganizerInvites() != null && jam.getPendingOrganizerInvites().stream().anyMatch(invite -> roleId.equals(invite.roleId()))) {
            throw new IllegalArgumentException("Remove this role's members and invitations before deleting it.");
        }
        List<Modjam.OrganizerRole> roles = mutable(jam.getOrganizerRoles());
        if (!roles.removeIf(role -> roleId.equals(role.id()))) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizer role not found.");
        jam.setOrganizerRoles(roles);
        persist(jam, "organizerRoles", roles);
        return jam;
    }

    public Modjam invite(String jamId, String hostId, String username, String roleId) {
        Modjam jam = owned(jamId, hostId);
        if (jam.getOrganizerRoles() == null || jam.getOrganizerRoles().stream().noneMatch(role -> Objects.equals(role.id(), roleId))) {
            throw new IllegalArgumentException("Choose an existing organizer role.");
        }
        if (username == null || username.isBlank() || username.length() > 50) throw new IllegalArgumentException("Choose a username.");
        User user = users.findByUsernameIgnoreCase(username.trim()).orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (Objects.equals(user.getId(), hostId) || isOrganizer(jam, user.getId())) throw new IllegalArgumentException("User is already an organizer.");
        List<Modjam.OrganizerInvite> invites = mutable(jam.getPendingOrganizerInvites());
        if (invites.stream().anyMatch(invite -> user.getId().equals(invite.userId()))) throw new IllegalArgumentException("User is already invited.");
        if (invites.size() >= 50) throw new IllegalArgumentException("A jam supports up to 50 pending organizer invitations.");
        invites.add(new Modjam.OrganizerInvite(user.getId(), user.getUsername(), roleId));
        jam.setPendingOrganizerInvites(invites);
        var persisted = mongo.updateFirst(Query.query(Criteria.where("_id").is(jamId).and("pendingOrganizerInvites.userId").ne(user.getId())),
                new Update().addToSet("pendingOrganizerInvites", invites.getLast()).set("updatedAt", java.time.Instant.now()), Modjam.class);
        if (persisted.getMatchedCount() == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "This invitation changed. Refresh the jam and try again.");
        org.bson.Document notification = new org.bson.Document("userId", user.getId())
                .append("title", "Jam organizer invitation")
                .append("message", "You have been invited to organize " + jam.getTitle())
                .append("link", "/jam/" + jam.getSlug() + "/overview")
                .append("read", false).append("createdAt", java.time.Instant.now());
        mongo.save(notification, "notifications");
        return jam;
    }

    public Modjam answerInvite(String jamId, String userId, boolean accept) {
        Modjam jam = find(jamId);
        List<Modjam.OrganizerInvite> invites = mutable(jam.getPendingOrganizerInvites());
        Modjam.OrganizerInvite invite = invites.stream().filter(value -> Objects.equals(userId, value.userId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No pending organizer invitation."));
        if (accept && (jam.getOrganizerRoles() == null || jam.getOrganizerRoles().stream().noneMatch(role -> Objects.equals(role.id(), invite.roleId())))) {
            throw new IllegalArgumentException("The invited role no longer exists.");
        }
        invites.removeIf(value -> Objects.equals(userId, value.userId()));
        List<Modjam.OrganizerMember> members = mutable(jam.getOrganizerMembers());
        if (accept && members.stream().noneMatch(member -> Objects.equals(userId, member.userId()))) members.add(new Modjam.OrganizerMember(userId, invite.roleId()));
        jam.setPendingOrganizerInvites(invites);
        jam.setOrganizerMembers(members);
        Update mutation = new Update().pull("pendingOrganizerInvites", new org.bson.Document("userId", userId)).set("updatedAt", java.time.Instant.now());
        if (accept) mutation.addToSet("organizerMembers", new Modjam.OrganizerMember(userId, invite.roleId()));
        var persisted = mongo.updateFirst(Query.query(Criteria.where("_id").is(jamId).and("pendingOrganizerInvites.userId").is(userId)), mutation, Modjam.class);
        if (persisted.getMatchedCount() == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "This invitation is no longer pending.");
        return jam;
    }

    public Modjam remove(String jamId, String hostId, String userId) {
        Modjam jam = owned(jamId, hostId);
        if (Objects.equals(hostId, userId)) throw new IllegalArgumentException("The host cannot be removed.");
        List<Modjam.OrganizerMember> members = mutable(jam.getOrganizerMembers());
        List<Modjam.OrganizerInvite> invites = mutable(jam.getPendingOrganizerInvites());
        members.removeIf(member -> Objects.equals(userId, member.userId()));
        invites.removeIf(invite -> Objects.equals(userId, invite.userId()));
        jam.setOrganizerMembers(members);
        jam.setPendingOrganizerInvites(invites);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(jamId)),
                new Update().pull("organizerMembers", new org.bson.Document("userId", userId))
                        .pull("pendingOrganizerInvites", new org.bson.Document("userId", userId)).set("updatedAt", java.time.Instant.now()), Modjam.class);
        return jam;
    }

    private Modjam owned(String id, String userId) {
        Modjam jam = find(id);
        if (!Objects.equals(jam.getHostId(), userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can manage organizer roles.");
        return jam;
    }

    private Modjam find(String id) { return jams.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found.")); }
    private void persist(Modjam jam, String field, Object value) { mongo.updateFirst(Query.query(Criteria.where("_id").is(jam.getId())), new Update().set(field, value).set("updatedAt", java.time.Instant.now()), Modjam.class); }
    private static <T> List<T> mutable(List<T> values) { return values == null ? new ArrayList<>() : new ArrayList<>(values); }
}
