package net.modtale.model.jam;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** A durable milestone, with no webhook credentials persisted in the document. */
@Document(collection = "modjam_discord_outbox")
@CompoundIndex(name = "modjam_feed_due", def = "{'state': 1, 'nextAttemptAt': 1, 'leaseUntil': 1}")
public class ModjamDiscordEvent {
    public enum Milestone { CREATED, SUBMISSIONS_OPENED, VOTING_OPENED, WINNERS_ANNOUNCED }
    public enum State { PENDING, DELIVERED, DISCARDED }

    @Id private String id;
    private String jamId;
    private Milestone milestone;
    private Instant occurredAt;
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private String leaseToken;
    private State state = State.PENDING;
    private int attempts;
    private Instant deliveredAt;
    private Integer lastStatus;

    public ModjamDiscordEvent() {}

    public ModjamDiscordEvent(String jamId, Milestone milestone, Instant occurredAt, Instant now) {
        this.id = "modjam:" + jamId + ":" + milestone.name();
        this.jamId = jamId;
        this.milestone = milestone;
        this.occurredAt = occurredAt;
        this.nextAttemptAt = now;
    }

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getJamId() { return jamId; }
    public void setJamId(String value) { jamId = value; }
    public Milestone getMilestone() { return milestone; }
    public void setMilestone(Milestone value) { milestone = value; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant value) { occurredAt = value; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant value) { nextAttemptAt = value; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant value) { leaseUntil = value; }
    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String value) { leaseToken = value; }
    public State getState() { return state; }
    public void setState(State value) { state = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant value) { deliveredAt = value; }
    public Integer getLastStatus() { return lastStatus; }
    public void setLastStatus(Integer value) { lastStatus = value; }
}
