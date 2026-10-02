package net.modtale.service.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.modtale.model.dto.request.finance.StageAdSettlementRequest;
import net.modtale.model.finance.AdSettlementDepositClaim;
import net.modtale.model.finance.AdSettlementStage;
import net.modtale.model.finance.AdSettlementStage.*;
import net.modtale.model.user.AdminPermission;
import net.modtale.model.user.User;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Review-only: no provider calls, wallet dependency, funding flag or release endpoint. */
@Service
public class AdSettlementStagingService {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final MongoTemplate mongo;
    private final AdSettlementActivityService activity;
    private final TransactionTemplate transactions;
    public AdSettlementStagingService(MongoTemplate mongo, MongoDatabaseFactory factory, AdSettlementActivityService activity) {
        this.mongo = mongo; this.activity = activity;
        transactions = new TransactionTemplate(new MongoTransactionManager(factory));
    }
    public record Amendment(int expectedRevision, String operationId, StageAdSettlementRequest report, String reason) {}
    public record Review(int expectedRevision, String operationId, String decision, String reason) {}
    public List<Map<String, Object>> list(User actor) {
        requireReviewer(actor);
        return mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "updatedAt")).limit(30), AdSettlementStage.class).stream().map(stage -> {
            Snapshot snapshot = stage.snapshots().getLast();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", stage.id()); row.put("provider", stage.provider()); row.put("reportId", stage.reportId());
            row.put("currency", stage.currency()); row.put("revision", stage.revision()); row.put("status", stage.status());
            row.put("reportedCollectedCents", snapshot.reportedCollectedCents()); row.put("from", snapshot.from()); row.put("through", snapshot.through());
            row.put("fundingVerified", false); row.put("creditReleaseEnabled", false); return row;
        }).toList();
    }
    public AdSettlementStage get(User actor, String id) {
        requireReviewer(actor);
        AdSettlementStage stage = mongo.findById(id, AdSettlementStage.class);
        if (stage == null) throw new IllegalArgumentException("Staged report not found.");
        return stage;
    }
    public AdSettlementStage create(User actor, StageAdSettlementRequest input) {
        requireReviewer(actor); validate(input);
        String id = sourceId("report", input.provider(), input.providerAccount(), input.reportId());
        String digest = digest(input);
        AdSettlementStage existing = mongo.findById(id, AdSettlementStage.class);
        if (existing != null) return replay(existing, digest);
        Snapshot snapshot = snapshot(input, 1);
        AdSettlementStage stage = new AdSettlementStage(id, input.provider(), input.providerAccount(), input.reportId(), input.depositId(), input.currency(),
                1, "AWAITING_REVIEW", List.of(snapshot), List.of(new AuditEvent("create", "STAGED", 1, actor.getId(),
                "Reported deposit and activity remain unverified.", digest, Instant.now())), Instant.now());
        try {
            return transactions.execute(status -> {
                mongo.insert(new AdSettlementDepositClaim(sourceId("deposit", input.provider(), input.providerAccount(), input.depositId()), id));
                return mongo.insert(stage);
            });
        } catch (DuplicateKeyException duplicate) {
            existing = mongo.findById(id, AdSettlementStage.class);
            if (existing != null) return replay(existing, digest);
            throw new IllegalArgumentException("This deposit identity is already claimed by another staged report.");
        }
    }
    public AdSettlementStage amend(User actor, String id, Amendment amendment) {
        AdSettlementStage stage = get(actor, id);
        if (amendment == null) throw new IllegalArgumentException("Amendment required.");
        validate(amendment.report()); requireOperation(amendment.operationId()); requireReason(amendment.reason());
        StageAdSettlementRequest input = amendment.report();
        if (!stage.provider().equals(input.provider()) || !stage.providerAccount().equals(input.providerAccount()) || !stage.reportId().equals(input.reportId())
                || !stage.depositId().equals(input.depositId()) || !stage.currency().equals(input.currency())) throw new IllegalArgumentException("Report, deposit, account and currency identities cannot be changed by an amendment.");
        String digest = digest(amendment);
        if (hasOperation(stage, amendment.operationId(), digest)) return stage;
        if (stage.revision() != amendment.expectedRevision() || stage.revision() >= 10) throw new IllegalArgumentException("Reload the report before amending; at most 10 immutable revisions are supported.");
        Snapshot snapshot = snapshot(input, stage.revision() + 1);
        AuditEvent event = new AuditEvent(amendment.operationId(), "AMENDED", snapshot.revision(), actor.getId(), amendment.reason(), digest, Instant.now());
        var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("revision").is(stage.revision()).and("status").is(stage.status()).and("audit.operationId").ne(amendment.operationId())),
                new Update().push("snapshots", snapshot).push("audit", event).set("revision", snapshot.revision()).set("status", "AWAITING_REVIEW").set("updatedAt", Instant.now()), AdSettlementStage.class);
        if (result.getModifiedCount() != 1) throw new IllegalArgumentException("The report changed; reload it before amending.");
        return get(actor, id);
    }
    public AdSettlementStage review(User actor, String id, Review review) {
        AdSettlementStage stage = get(actor, id);
        if (review == null) throw new IllegalArgumentException("Review required.");
        requireOperation(review.operationId()); requireReason(review.reason());
        if (review.decision() == null || !List.of("REVIEWED_PROVISIONAL", "REJECTED").contains(review.decision())) throw new IllegalArgumentException("Review records provisional acceptance or rejection only; it cannot verify funding or release credit.");
        String digest = digest(review);
        if (hasOperation(stage, review.operationId(), digest)) return stage;
        if (stage.revision() != review.expectedRevision() || !"AWAITING_REVIEW".equals(stage.status())) throw new IllegalArgumentException("This revision has already changed or been reviewed.");
        var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("revision").is(stage.revision()).and("status").is("AWAITING_REVIEW")),
                new Update().push("audit", new AuditEvent(review.operationId(), review.decision(), stage.revision(), actor.getId(), review.reason(), digest, Instant.now()))
                        .set("status", review.decision()).set("updatedAt", Instant.now()), AdSettlementStage.class);
        if (result.getModifiedCount() != 1) throw new IllegalArgumentException("The report changed; reload it before reviewing.");
        return get(actor, id);
    }
    private Snapshot snapshot(StageAdSettlementRequest input, int revision) {
        List<Activity> rows = activity.snapshot(input.from(), input.through());
        Map<String, Long> weights = new LinkedHashMap<>();
        rows.stream().filter(row -> row.provisionalPoints() > 0).forEach(row -> weights.put(row.projectId(), row.provisionalPoints()));
        long pool = FinanceAmounts.share(input.exactCents(), 7500);
        Map<String, Long> cents = weights.isEmpty() ? Map.of() : RevenuePoolAllocator.allocate(input.exactCents(), 7500, weights).projectCents();
        List<Activity> allocated = rows.stream().map(row -> new Activity(row.projectId(), row.creatorId(), row.title(), row.pageviews(), row.launcherDownloads(),
                row.frontendDownloads(), row.apiDownloads(), row.provisionalPoints(), cents.getOrDefault(row.projectId(), 0L), row.eligibilityNote())).toList();
        return new Snapshot(revision, input.from(), input.through(), input.exactCents(), input.reportSha256(), digest(input), digest(rows), 7500,
                pool, input.exactCents() - pool, weights.isEmpty() ? pool : 0, AdSettlementActivityService.RULE, allocated, Instant.now());
    }
    private static AdSettlementStage replay(AdSettlementStage existing, String digest) {
        if (!existing.snapshots().getFirst().inputDigest().equals(digest)) throw new IllegalArgumentException("Conflicting report replay. Submit an explicit amendment with a reason.");
        return existing;
    }
    private static boolean hasOperation(AdSettlementStage stage, String operationId, String digest) {
        for (AuditEvent event : stage.audit()) if (event.operationId().equals(operationId)) {
            if (!event.inputDigest().equals(digest)) throw new IllegalArgumentException("This operation ID belongs to different review content.");
            return true;
        }
        return false;
    }
    public static void requireReviewer(User actor) {
        if (actor == null || actor.getId() == null || actor.getId().isBlank() || actor.isDeleted() || !AdminPermission.hasPermission(actor, AdminPermission.PLATFORM_FINANCE_MANAGE)) throw new SecurityException("Finance review permission is required.");
    }
    static void validate(StageAdSettlementRequest input) {
        if (input == null) throw new IllegalArgumentException("Report required.");
        if (input.provider() == null || !input.provider().matches("[a-z0-9][a-z0-9._-]{0,79}")) throw new IllegalArgumentException("Use a lowercase provider key of at most 80 characters.");
        for (String id : List.of(input.provider() == null ? "" : input.provider(), input.providerAccount() == null ? "" : input.providerAccount(),
                input.reportId() == null ? "" : input.reportId(), input.depositId() == null ? "" : input.depositId())) {
            if (!id.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,119}")) throw new IllegalArgumentException("Use non-secret provider/report/deposit identifiers of at most 120 characters.");
        }
        if (!"usd".equals(input.currency())) throw new IllegalArgumentException("Staging currently supports USD settlement reports only.");
        if (input.exactCents() <= 0 || input.exactCents() > 1_000_000_000_000L) throw new IllegalArgumentException("Reported collected cents must be a positive bounded integer.");
        if (input.from() == null || input.through() == null || input.through().isBefore(input.from()) || !input.through().isBefore(LocalDate.now(ZoneOffset.UTC))
                || input.from().plusDays(365).isBefore(input.through())) throw new IllegalArgumentException("Choose a closed UTC reporting period of no more than 366 days.");
        if (input.reportSha256() == null || !input.reportSha256().matches("[a-f0-9]{64}")) throw new IllegalArgumentException("A SHA-256 digest of the source report is required; this digest is not funding verification.");
    }
    private static void requireOperation(String id) { try { UUID.fromString(id); } catch (RuntimeException invalid) { throw new IllegalArgumentException("A unique review operation ID is required."); } }
    private static void requireReason(String reason) { if (reason == null || reason.isBlank() || reason.length() > 1000) throw new IllegalArgumentException("A review reason of 1–1000 characters is required."); }
    static String sourceId(String type, String provider, String account, String source) { return "ad-" + type + ":" + digest(List.of(provider, account, source)); }
    static String digest(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsString(value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception invalid) { throw new IllegalArgumentException("Could not fingerprint report evidence.", invalid); }
    }
}
