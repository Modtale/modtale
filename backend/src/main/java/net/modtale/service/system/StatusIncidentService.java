package net.modtale.service.system;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.dto.request.admin.CreateStatusIncidentRequest;
import net.modtale.model.dto.request.admin.UpdateStatusIncidentRequest;
import net.modtale.model.dto.response.system.StatusIncidentUpdateView;
import net.modtale.model.dto.response.system.StatusIncidentView;
import net.modtale.model.system.StatusIncident;
import net.modtale.model.system.StatusIncidentKind;
import net.modtale.model.system.StatusIncidentState;
import net.modtale.model.system.SystemStatus;
import net.modtale.model.user.User;
import net.modtale.repository.system.StatusIncidentRepository;
import net.modtale.service.admin.audit.AdminAuditLogger;
import org.springframework.stereotype.Service;

@Service
public class StatusIncidentService {

    private final StatusIncidentRepository statusIncidentRepository;
    private final AdminAuditLogger adminAuditLogger;

    public StatusIncidentService(
            StatusIncidentRepository statusIncidentRepository,
            AdminAuditLogger adminAuditLogger
    ) {
        this.statusIncidentRepository = statusIncidentRepository;
        this.adminAuditLogger = adminAuditLogger;
    }

    public List<StatusIncidentView> getAdminIncidents() {
        return statusIncidentRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    public List<StatusIncidentView> getActiveIncidents() {
        LocalDateTime now = LocalDateTime.now();
        return statusIncidentRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .filter(incident -> !incident.isClosed())
                .filter(incident -> incident.getKind() == StatusIncidentKind.INCIDENT
                        || incident.getState() != StatusIncidentState.SCHEDULED
                        || isScheduledMaintenanceActive(incident, now))
                .sorted(Comparator.comparing(StatusIncident::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toView)
                .toList();
    }

    public List<StatusIncidentView> getScheduledMaintenances() {
        LocalDateTime now = LocalDateTime.now();
        return statusIncidentRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .filter(incident -> !incident.isClosed())
                .filter(incident -> incident.getKind() == StatusIncidentKind.MAINTENANCE)
                .filter(incident -> incident.getState() == StatusIncidentState.SCHEDULED)
                .filter(incident -> startsInFuture(incident, now))
                .sorted(Comparator.comparing(StatusIncident::getScheduledStart, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toView)
                .toList();
    }

    public List<StatusIncidentView> getIncidentHistory() {
        return statusIncidentRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .filter(StatusIncident::isClosed)
                .limit(20)
                .map(this::toView)
                .toList();
    }

    public StatusIncidentView createIncident(User admin, CreateStatusIncidentRequest request) {
        LocalDateTime now = LocalDateTime.now();
        StatusIncident incident = new StatusIncident();
        incident.setKind(request.getKind());
        incident.setTitle(request.getTitle().trim());
        incident.setImpact(request.getImpact());
        incident.setState(resolveInitialState(request));
        incident.setAffectedServices(cleanAffectedServices(request.getAffectedServices()));
        incident.setScheduledStart(request.getScheduledStart());
        incident.setScheduledEnd(request.getScheduledEnd());
        incident.setCreatedAt(now);
        incident.setUpdatedAt(now);
        incident.setCreatedBy(admin.getId());
        incident.setCreatedByUsername(admin.getUsername());

        if (incident.getState() != StatusIncidentState.SCHEDULED) {
            incident.setStartedAt(now);
        }
        if (incident.isClosed()) {
            incident.setResolvedAt(now);
        }

        incident.addUpdate(newUpdate(admin, incident.getState(), incident.getImpact(), request.getMessage(), now));
        StatusIncident saved = statusIncidentRepository.save(incident);

        adminAuditLogger.logAction(admin.getId(), "CREATE_STATUS_INCIDENT", saved.getId(), "STATUS_INCIDENT",
                saved.getKind() + ": " + saved.getTitle());
        return toView(saved);
    }

    public StatusIncidentView appendUpdate(User admin, String incidentId, UpdateStatusIncidentRequest request) {
        StatusIncident incident = statusIncidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Status incident not found."));

        LocalDateTime now = LocalDateTime.now();
        incident.setState(request.getState());
        incident.setImpact(request.getImpact());
        incident.setUpdatedAt(now);

        if (request.getScheduledEnd() != null) {
            incident.setScheduledEnd(request.getScheduledEnd());
        }
        if (incident.getStartedAt() == null && request.getState() != StatusIncidentState.SCHEDULED) {
            incident.setStartedAt(now);
        }
        if ((request.getState() == StatusIncidentState.RESOLVED || request.getState() == StatusIncidentState.CANCELED)
                && incident.getResolvedAt() == null) {
            incident.setResolvedAt(now);
        }

        incident.addUpdate(newUpdate(admin, request.getState(), request.getImpact(), request.getMessage(), now));
        StatusIncident saved = statusIncidentRepository.save(incident);

        adminAuditLogger.logAction(admin.getId(), "UPDATE_STATUS_INCIDENT", saved.getId(), "STATUS_INCIDENT",
                saved.getState() + ": " + saved.getTitle());
        return toView(saved);
    }

    private StatusIncidentState resolveInitialState(CreateStatusIncidentRequest request) {
        if (request.getState() != null) {
            return request.getState();
        }
        if (request.getKind() == StatusIncidentKind.MAINTENANCE && request.getScheduledStart() != null
                && request.getScheduledStart().isAfter(LocalDateTime.now())) {
            return StatusIncidentState.SCHEDULED;
        }
        return StatusIncidentState.INVESTIGATING;
    }

    private List<String> cleanAffectedServices(List<String> affectedServices) {
        if (affectedServices == null) {
            return List.of();
        }
        return affectedServices.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase())
                .distinct()
                .toList();
    }

    private StatusIncident.StatusIncidentUpdate newUpdate(
            User admin,
            StatusIncidentState state,
            SystemStatus impact,
            String message,
            LocalDateTime createdAt
    ) {
        return new StatusIncident.StatusIncidentUpdate(
                state,
                impact,
                message.trim(),
                createdAt,
                admin.getId(),
                admin.getUsername()
        );
    }

    private boolean startsInFuture(StatusIncident incident, LocalDateTime now) {
        return incident.getScheduledStart() != null && incident.getScheduledStart().isAfter(now);
    }

    private boolean isScheduledMaintenanceActive(StatusIncident incident, LocalDateTime now) {
        return incident.getKind() == StatusIncidentKind.MAINTENANCE
                && incident.getState() == StatusIncidentState.SCHEDULED
                && incident.getScheduledStart() != null
                && !incident.getScheduledStart().isAfter(now)
                && (incident.getScheduledEnd() == null || incident.getScheduledEnd().isAfter(now));
    }

    private StatusIncidentView toView(StatusIncident incident) {
        List<StatusIncidentUpdateView> updates = incident.getUpdates() == null
                ? List.of()
                : incident.getUpdates().stream()
                .sorted(Comparator.comparing(StatusIncident.StatusIncidentUpdate::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(update -> new StatusIncidentUpdateView(
                        update.getId(),
                        update.getState(),
                        update.getImpact(),
                        update.getMessage(),
                        update.getCreatedAt(),
                        update.getCreatedByUsername()
                ))
                .toList();

        return new StatusIncidentView(
                incident.getId(),
                incident.getKind(),
                incident.getState(),
                incident.getImpact(),
                incident.getTitle(),
                incident.getAffectedServices() != null ? incident.getAffectedServices() : List.of(),
                incident.getScheduledStart(),
                incident.getScheduledEnd(),
                incident.getStartedAt(),
                incident.getResolvedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getCreatedByUsername(),
                updates
        );
    }
}
