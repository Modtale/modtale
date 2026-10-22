package net.modtale.service.jam;

import java.time.LocalDateTime;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanStatus;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class ModjamProjectReleasePersistence {
    private final MongoTemplate mongo;

    public ModjamProjectReleasePersistence(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Project claimRelease(Project snapshot) {
        Criteria guard = Criteria.where("_id").is(snapshot.getId())
                .and("status").is(ProjectStatus.PRIVATE)
                .and("modjamPublicationPending").is(true)
                .and("deletedAt").is(null)
                .and("updatedAt").is(snapshot.getUpdatedAt())
                .and("classification").is(snapshot.getClassification())
                .and("modjamIds").is(snapshot.getModjamIds());
        Criteria noScan = Criteria.where("versions").not().elemMatch(Criteria.where("scanResult.status").is(ScanStatus.SCANNING));
        Criteria conditions = snapshot.getClassification() == ProjectClassification.MODPACK
                ? new Criteria().andOperator(guard, noScan)
                : new Criteria().andOperator(guard, noScan, Criteria.where("versions").elemMatch(
                        Criteria.where("reviewStatus").is(ProjectVersion.ReviewStatus.APPROVED)));
        Update change = new Update().set("status", ProjectStatus.PUBLISHED)
                .set("modjamPublicationPending", false)
                .set("updatedAt", LocalDateTime.now().toString())
                .set("rankingDirty", true);
        // Claim once and change only publication fields. A canceled private choice,
        // changed project snapshot or second scheduler instance cannot win this claim.
        return mongo.findAndModify(Query.query(conditions), change,
                FindAndModifyOptions.options().returnNew(true), Project.class);
    }
}
