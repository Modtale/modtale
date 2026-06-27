package net.modtale.status;

import com.mongodb.MongoException;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.modtale.status.StatusModels.IncidentBuckets;
import net.modtale.status.StatusModels.StatusHistoryEntry;
import net.modtale.status.StatusModels.StatusIncidentKind;
import net.modtale.status.StatusModels.StatusIncidentState;
import net.modtale.status.StatusModels.StatusIncidentUpdateView;
import net.modtale.status.StatusModels.StatusIncidentView;
import net.modtale.status.StatusModels.SystemStatus;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class MongoStatusStore implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MongoStatusStore.class);

    private final StatusServiceProperties properties;
    private volatile MongoClient mongoClient;

    public MongoStatusStore(StatusServiceProperties properties) {
        this.properties = properties;
    }

    public Optional<StatusHistoryEntry> findLatestHistory() {
        return withDatabase(database -> {
            Document document = history(database)
                    .find()
                    .sort(Sorts.descending("timestamp"))
                    .first();
            return Optional.ofNullable(document).map(this::toHistoryEntry);
        }).orElse(Optional.empty());
    }

    public List<StatusHistoryEntry> findHistoryAfter(Instant since) {
        return withDatabase(database -> {
            FindIterable<Document> documents = history(database)
                    .find(Filters.gte("timestamp", Date.from(since)))
                    .sort(Sorts.ascending("timestamp"));
            List<StatusHistoryEntry> entries = new ArrayList<>();
            for (Document document : documents) {
                entries.add(toHistoryEntry(document));
            }
            return entries;
        }).orElse(List.of());
    }

    public void saveHistory(StatusHistoryEntry entry) {
        withDatabase(database -> {
            history(database).insertOne(new Document()
                    .append("timestamp", Date.from(entry.timestamp()))
                    .append("siteLatency", entry.siteLatency())
                    .append("apiLatency", entry.apiLatency())
                    .append("dbLatency", entry.dbLatency())
                    .append("storageLatency", entry.storageLatency())
                    .append("overallStatus", entry.overallStatus().name())
                    .append("siteStatus", entry.siteStatus().name())
                    .append("apiStatus", entry.apiStatus().name())
                    .append("dbStatus", entry.dbStatus().name())
                    .append("storageStatus", entry.storageStatus().name()));
            return null;
        });
    }

    public IncidentBuckets findIncidentBuckets() {
        List<StatusIncidentView> incidents = withDatabase(database -> {
            List<StatusIncidentView> views = new ArrayList<>();
            for (Document document : incidents(database)
                    .find()
                    .sort(Sorts.descending("createdAt"))
                    .limit(50)) {
                views.add(toIncidentView(document));
            }
            return views;
        }).orElse(List.of());

        if (incidents.isEmpty()) {
            return IncidentBuckets.empty();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<StatusIncidentView> active = incidents.stream()
                .filter(incident -> !incident.isClosed())
                .filter(incident -> incident.kind() == StatusIncidentKind.INCIDENT
                        || incident.state() != StatusIncidentState.SCHEDULED
                        || isScheduledMaintenanceActive(incident, now))
                .sorted(Comparator.comparing(StatusIncidentView::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        List<StatusIncidentView> scheduled = incidents.stream()
                .filter(incident -> !incident.isClosed())
                .filter(incident -> incident.kind() == StatusIncidentKind.MAINTENANCE)
                .filter(incident -> incident.state() == StatusIncidentState.SCHEDULED)
                .filter(incident -> incident.scheduledStart() != null && incident.scheduledStart().isAfter(now))
                .sorted(Comparator.comparing(StatusIncidentView::scheduledStart, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<StatusIncidentView> history = incidents.stream()
                .filter(StatusIncidentView::isClosed)
                .limit(20)
                .toList();

        return new IncidentBuckets(active, scheduled, history);
    }

    @Override
    public void close() {
        MongoClient client = mongoClient;
        if (client != null) {
            client.close();
        }
    }

    private MongoCollection<Document> history(MongoDatabase database) {
        return database.getCollection("status_history");
    }

    private MongoCollection<Document> incidents(MongoDatabase database) {
        return database.getCollection("status_incidents");
    }

    private <T> Optional<T> withDatabase(DatabaseOperation<T> operation) {
        if (!hasText(properties.getMongoUri())) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(operation.run(client().getDatabase(properties.getMongoDatabase())));
        } catch (MongoException | IllegalArgumentException e) {
            logger.warn("Detached status store could not reach MongoDB: {}", e.toString());
            return Optional.empty();
        }
    }

    private MongoClient client() {
        MongoClient current = mongoClient;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (mongoClient == null) {
                mongoClient = MongoClients.create(mongoSettings());
            }
            return mongoClient;
        }
    }

    private MongoClientSettings mongoSettings() {
        long timeoutMillis = Math.max(1, properties.getRequestTimeout().toMillis());
        return MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(properties.getMongoUri()))
                .applyToClusterSettings(settings -> settings.serverSelectionTimeout(timeoutMillis, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(settings -> {
                    settings.connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
                    settings.readTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
                })
                .build();
    }

    private StatusHistoryEntry toHistoryEntry(Document document) {
        SystemStatus overall = status(document.get("overallStatus"), SystemStatus.DEGRADED);
        return new StatusHistoryEntry(
                instant(document.get("timestamp")).orElse(Instant.now()),
                number(document.get("siteLatency")),
                number(document.get("apiLatency")),
                number(document.get("dbLatency")),
                number(document.get("storageLatency")),
                overall,
                status(document.get("siteStatus"), status(document.get("apiStatus"), overall)),
                status(document.get("apiStatus"), overall),
                status(document.get("dbStatus"), overall),
                status(document.get("storageStatus"), overall)
        );
    }

    private StatusIncidentView toIncidentView(Document document) {
        List<StatusIncidentUpdateView> updates = updateDocuments(document).stream()
                .map(this::toIncidentUpdateView)
                .sorted(Comparator.comparing(StatusIncidentUpdateView::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        return new StatusIncidentView(
                text(document.get("_id")),
                incidentKind(document.get("kind")),
                incidentState(document.get("state")),
                status(document.get("impact"), SystemStatus.DEGRADED),
                text(document.get("title")),
                stringList(document.get("affectedServices")),
                localDateTime(document.get("scheduledStart")).orElse(null),
                localDateTime(document.get("scheduledEnd")).orElse(null),
                localDateTime(document.get("startedAt")).orElse(null),
                localDateTime(document.get("resolvedAt")).orElse(null),
                localDateTime(document.get("createdAt")).orElse(null),
                localDateTime(document.get("updatedAt")).orElse(null),
                text(document.get("createdByUsername")),
                updates
        );
    }

    private StatusIncidentUpdateView toIncidentUpdateView(Document document) {
        return new StatusIncidentUpdateView(
                text(document.get("id")),
                incidentState(document.get("state")),
                status(document.get("impact"), SystemStatus.DEGRADED),
                text(document.get("message")),
                localDateTime(document.get("createdAt")).orElse(null),
                text(document.get("createdByUsername"))
        );
    }

    private List<Document> updateDocuments(Document document) {
        Object raw = document.get("updates");
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .toList();
    }

    private boolean isScheduledMaintenanceActive(StatusIncidentView incident, LocalDateTime now) {
        return incident.kind() == StatusIncidentKind.MAINTENANCE
                && incident.state() == StatusIncidentState.SCHEDULED
                && incident.scheduledStart() != null
                && !incident.scheduledStart().isAfter(now)
                && (incident.scheduledEnd() == null || incident.scheduledEnd().isAfter(now));
    }

    private Optional<Instant> instant(Object value) {
        if (value instanceof Date date) {
            return Optional.of(date.toInstant());
        }
        if (value instanceof Instant instant) {
            return Optional.of(instant);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Optional.of(localDateTime.toInstant(ZoneOffset.UTC));
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Instant.parse(text));
            } catch (DateTimeParseException ignored) {
                try {
                    return Optional.of(LocalDateTime.parse(text).toInstant(ZoneOffset.UTC));
                } catch (DateTimeParseException ignoredAgain) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private Optional<LocalDateTime> localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return Optional.of(localDateTime);
        }
        return instant(value).map(instant -> LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String text(Object value) {
        return value != null ? value.toString() : "";
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(item -> item != null && !item.toString().isBlank())
                .map(item -> item.toString().trim())
                .distinct()
                .toList();
    }

    private SystemStatus status(Object value, SystemStatus fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return SystemStatus.valueOf(value.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private StatusIncidentKind incidentKind(Object value) {
        if (value == null) {
            return StatusIncidentKind.INCIDENT;
        }
        try {
            return StatusIncidentKind.valueOf(value.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return StatusIncidentKind.INCIDENT;
        }
    }

    private StatusIncidentState incidentState(Object value) {
        if (value == null) {
            return StatusIncidentState.INVESTIGATING;
        }
        try {
            return StatusIncidentState.valueOf(value.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return StatusIncidentState.INVESTIGATING;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface DatabaseOperation<T> {
        T run(MongoDatabase database);
    }
}
