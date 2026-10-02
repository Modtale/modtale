package net.modtale.service.jam;

import java.util.List;
import net.modtale.model.jam.ModjamSubmission;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class ModjamVotePersistence {
    private final MongoTemplate mongo;

    public ModjamVotePersistence(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public ModjamSubmission replaceBallot(String jamId, String submissionId, ModjamSubmission.Vote vote) {
        Document ballot = new Document("_id", vote.getId())
                .append("voterId", vote.getVoterId()).append("categoryId", vote.getCategoryId())
                .append("score", vote.getScore()).append("isJudge", vote.isJudge());
        Document remaining = new Document("$filter", new Document("input",
                new Document("$ifNull", List.of("$votes", List.of())))
                .append("as", "ballot")
                .append("cond", new Document("$not", List.of(new Document("$and", List.of(
                        new Document("$eq", List.of("$$ballot.voterId", new Document("$literal", vote.getVoterId()))),
                        new Document("$eq", List.of("$$ballot.categoryId", new Document("$literal", vote.getCategoryId())))
                ))))));
        Document fields = new Document("votes", new Document("$concatArrays", List.of(remaining,
                new Document("$literal", List.of(ballot)))))
                .append("voteRevision", new Document("$add", List.of(new Document("$ifNull", List.of("$voteRevision", 0)), 1)));
        AggregationUpdate update = AggregationUpdate.from(List.of(context -> new Document("$set", fields)));
        return mongo.findAndModify(Query.query(Criteria.where("_id").is(submissionId).and("jamId").is(jamId)),
                update, FindAndModifyOptions.options().returnNew(true), ModjamSubmission.class);
    }

    public void saveScores(ModjamSubmission submission) {
        Criteria revision = submission.getVoteRevision() == 0
                ? new Criteria().orOperator(Criteria.where("voteRevision").is(0), Criteria.where("voteRevision").exists(false))
                : Criteria.where("voteRevision").is(submission.getVoteRevision());
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(submission.getId()).and("jamId").is(submission.getJamId()), revision));
        Update update = new Update().set("categoryScores", submission.getCategoryScores())
                .set("totalScore", submission.getTotalScore())
                .set("judgeCategoryScores", submission.getJudgeCategoryScores())
                .set("totalJudgeScore", submission.getTotalJudgeScore())
                .set("totalPublicScore", submission.getTotalPublicScore())
                .set("rank", submission.getRank());
        // If a ballot changed during calculation, this stale snapshot cannot
        // overwrite its fresh scores or any ballot/award/project metadata.
        mongo.updateFirst(query, update, ModjamSubmission.class);
    }

    public void saveAward(ModjamSubmission submission) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(submission.getId()).and("jamId").is(submission.getJamId())),
                new Update().set("isWinner", submission.isWinner()).set("awardTitle", submission.getAwardTitle()),
                ModjamSubmission.class);
    }
}
