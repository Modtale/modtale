package net.modtale.service.admin.review;

import org.bson.types.ObjectId;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/** Prepares immutable evidence for a proposed action; never mutates the project or starts work. */
public final class ReviewRepairPreparation {
    public record Request(String id,Object projectId,int versionIndex,String versionId,String expectedSha256,String actorId,ReviewSnapshotArchive.Action action) {
        public Request {
            if(id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    || !(projectId instanceof ObjectId || projectId instanceof String s && !s.isEmpty() && s.length()<=128)
                    || versionIndex<0 || versionIndex>16*1024*1024 || versionId==null || versionId.isBlank() || versionId.length()>128
                    || versionId.chars().anyMatch(Character::isISOControl) || expectedSha256==null || !expectedSha256.matches("[0-9a-f]{64}")
                    || actorId==null || actorId.isBlank() || actorId.length()>256 || action==null)throw new IllegalArgumentException("Invalid repair preparation");
        }
        @Override public String toString(){return "ReviewRepairPreparation["+id+"]";}
    }
    public record Prepared(String id,String sha256,long createdAt,long expiresAt) {}
    private final ReviewSnapshotArchive archive;
    private final RawReviewSnapshotReader reader;
    private final Clock clock;
    private final long lifetimeMillis;
    private final Semaphore slots;
    public ReviewRepairPreparation(ReviewSnapshotArchive archive,RawReviewSnapshotReader reader,Clock clock,int concurrency,long lifetimeMillis) {
        if(concurrency<1 || concurrency>2 || lifetimeMillis<1000 || lifetimeMillis>900000)throw new IllegalArgumentException("Invalid repair preparation limits");
        this.archive=Objects.requireNonNull(archive);this.reader=Objects.requireNonNull(reader);this.clock=Objects.requireNonNull(clock);this.lifetimeMillis=lifetimeMillis;slots=new Semaphore(concurrency);
    }
    public Prepared prepare(Request request,BooleanSupplier permitted) {
        Objects.requireNonNull(request);Objects.requireNonNull(permitted);requirePermission(permitted);
        if(!slots.tryAcquire())throw new IllegalStateException("Repair preparation is busy");
        try {
            var stored=archive.find(request.id());requirePermission(permitted);
            if(stored!=null && (!stored.projectId().equals(request.projectId()) || stored.versionIndex()!=request.versionIndex()
                    || !stored.actorId().equals(request.actorId()) || stored.action()!=request.action()))throw conflict();
            if(stored!=null)requireLive(stored);
            var captured=reader.capture(request.projectId(),request.versionIndex(),request.versionId());requirePermission(permitted);
            if(!captured.sha256().equals(request.expectedSha256()) || stored!=null && !Arrays.equals(stored.versionBytes(),captured.versionBytes()))throw conflict();
            if(stored==null) {
                long now=clock.millis();stored=archive.retain(captured.forArchive(request.id(),request.actorId(),request.action(),now,Math.addExact(now,lifetimeMillis)));
            }
            requirePermission(permitted);requireLive(stored);
            if(!reader.isCurrent(captured))throw conflict();
            requirePermission(permitted);requireLive(stored);
            return new Prepared(stored.id(),captured.sha256(),stored.createdAt(),stored.expiresAt());
        } finally {slots.release();}
    }
    private void requireLive(ReviewSnapshotArchive.Snapshot stored) {
        long now=clock.millis();if(now<stored.createdAt() || now>=stored.expiresAt())throw conflict();
    }
    private static void requirePermission(BooleanSupplier permitted){if(!permitted.getAsBoolean())throw new SecurityException("Repair preparation is not permitted");}
    private static IllegalStateException conflict(){return new IllegalStateException("Repair preparation changed or expired");}
}
