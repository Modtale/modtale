package net.modtale.service.admin.review;

import java.util.List;
import org.bson.Document;
import net.modtale.model.project.Project;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Service;

@Service
public class ProjectReviewQueueService {

    private final MongoTemplate mongoTemplate;

    public ProjectReviewQueueService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Project> getVerificationQueue() {
        Document match = Document.parse("""
                { "$and": [
                    { "$or": [
                        { "status": "PENDING" },
                        { "status": "PUBLISHED", "versions": {
                            "$elemMatch": { "reviewStatus": "PENDING" }
                        } }
                    ] },
                    { "versions": { "$not": {
                        "$elemMatch": { "scanResult.status": "SCANNING" }
                    } } }
                ] }
                """);
        // Shape the array on the server. A project can have dozens of historical versions, and
        // projecting dotted version fields still sends every one of them over the network.
        Document projection = Document.parse("""
                { "$project": {
                    "title": 1,
                    "description": { "$substrCP": [ { "$ifNull": ["$description", ""] }, 0, 240 ] },
                    "author": 1, "imageUrl": 1,
                    "classification": 1, "status": 1, "updatedAt": 1,
                    "versions": { "$let": {
                        "vars": { "v": { "$ifNull": [
                            { "$first": { "$filter": {
                                "input": "$versions", "as": "candidate",
                                "cond": { "$eq": ["$$candidate.reviewStatus", "PENDING"] }
                            } } },
                            { "$first": "$versions" }
                        ] } },
                        "in": { "$cond": [
                            { "$ne": ["$$v", null] },
                            [{
                                "_id": "$$v._id", "versionNumber": "$$v.versionNumber",
                                "changelog": { "$substrCP": [ { "$ifNull": ["$$v.changelog", ""] }, 0, 160 ] },
                                "reviewStatus": "$$v.reviewStatus",
                                "scanResult": { "$cond": [
                                    { "$ne": [ { "$ifNull": ["$$v.scanResult", null] }, null ] },
                                    {
                                        "status": "$$v.scanResult.status",
                                        "verdict": "$$v.scanResult.verdict",
                                        "riskScore": "$$v.scanResult.riskScore",
                                        "knownIssueCount": "$$v.scanResult.knownIssueCount",
                                        "newIssueCount": "$$v.scanResult.newIssueCount",
                                        "escalatedIssueCount": "$$v.scanResult.escalatedIssueCount"
                                    },
                                    null
                                ] }
                            }],
                            []
                        ] }
                    } }
                } }
                """);
        Aggregation aggregation = Aggregation.newAggregation(
                context -> new Document("$match", match),
                context -> projection
        );
        return mongoTemplate.aggregate(aggregation, "projects", Project.class).getMappedResults();
    }
}
