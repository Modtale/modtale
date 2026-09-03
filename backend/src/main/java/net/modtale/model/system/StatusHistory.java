package net.modtale.model.system;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "status_history")
public class StatusHistory {
    @Id
    private String id;

    @Indexed
    private LocalDateTime timestamp;

    private int apiLatency;
    private int dbLatency;
    private int storageLatency;

    private SystemStatus overallStatus;
    private SystemStatus apiStatus;
    private SystemStatus dbStatus;
    private SystemStatus storageStatus;

    public StatusHistory() {}

    public StatusHistory(int apiLatency, int dbLatency, int storageLatency, SystemStatus overallStatus) {
        this(
                apiLatency,
                dbLatency,
                storageLatency,
                overallStatus,
                overallStatus,
                overallStatus == SystemStatus.OPERATIONAL ? SystemStatus.OPERATIONAL : SystemStatus.DEGRADED,
                overallStatus == SystemStatus.OPERATIONAL ? SystemStatus.OPERATIONAL : SystemStatus.DEGRADED
        );
    }

    public StatusHistory(
            int apiLatency,
            int dbLatency,
            int storageLatency,
            SystemStatus overallStatus,
            SystemStatus apiStatus,
            SystemStatus dbStatus,
            SystemStatus storageStatus
    ) {
        this.timestamp = LocalDateTime.now();
        this.apiLatency = apiLatency;
        this.dbLatency = dbLatency;
        this.storageLatency = storageLatency;
        this.overallStatus = overallStatus;
        this.apiStatus = apiStatus;
        this.dbStatus = dbStatus;
        this.storageStatus = storageStatus;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public int getApiLatency() { return apiLatency; }
    public int getDbLatency() { return dbLatency; }
    public int getStorageLatency() { return storageLatency; }
    public SystemStatus getOverallStatus() { return overallStatus; }
    public SystemStatus getApiStatus() { return apiStatus; }
    public SystemStatus getDbStatus() { return dbStatus; }
    public SystemStatus getStorageStatus() { return storageStatus; }
}
