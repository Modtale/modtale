package net.modtale.model.analytics;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "status_history")
public class StatusHistory {
    @Id
    private String id;

    @Indexed
    private LocalDateTime timestamp;

    private int apiLatency;
    private int dbLatency;
    private int storageLatency;

    private String overallStatus;

    public StatusHistory() {}

    public StatusHistory(int apiLatency, int dbLatency, int storageLatency, String overallStatus) {
        this.timestamp = LocalDateTime.now();
        this.apiLatency = apiLatency;
        this.dbLatency = dbLatency;
        this.storageLatency = storageLatency;
        this.overallStatus = overallStatus;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public int getApiLatency() { return apiLatency; }
    public int getDbLatency() { return dbLatency; }
    public int getStorageLatency() { return storageLatency; }
    public String getOverallStatus() { return overallStatus; }
}