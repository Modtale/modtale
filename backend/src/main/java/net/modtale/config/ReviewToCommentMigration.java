package net.modtale.config;

import net.modtale.model.resources.Comment;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class ReviewToCommentMigration {

    private static final Logger logger = LoggerFactory.getLogger(ReviewToCommentMigration.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    private final Map<String, User> authorCache = new ConcurrentHashMap<>();

    @Bean
    public CommandLineRunner migrateReviews() {
        return args -> {
            logger.info("Starting migration: Reviews -> Comments...");

            Query query = new Query(Criteria.where("reviews").exists(true).ne(new ArrayList<>())
                    .and("comments").exists(false));

            List<Document> projectsWithReviews = mongoTemplate.find(query, Document.class, "projects");

            int count = 0;
            int skipped = 0;

            for (Document projectDoc : projectsWithReviews) {
                try {
                    String projectId = projectDoc.getString("_id");
                    String authorId = projectDoc.getString("authorId");
                    String authorName = projectDoc.getString("author");

                    List<Document> oldReviews = projectDoc.getList("reviews", Document.class);
                    if (oldReviews == null || oldReviews.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    List<Comment> newComments = new ArrayList<>();

                    User projectAuthor = getAuthor(authorId, authorName);

                    for (Document review : oldReviews) {
                        Comment comment = new Comment();

                        String oldId = review.getString("_id");
                        if (oldId == null) oldId = review.getString("id");
                        comment.setId(oldId != null ? oldId : UUID.randomUUID().toString());

                        comment.setUser(review.getString("user"));
                        comment.setUserAvatarUrl(review.getString("userAvatarUrl"));
                        comment.setContent(review.getString("comment"));

                        String date = review.getString("date");
                        comment.setDate(date != null ? date : java.time.LocalDate.now().toString());

                        comment.setUpdatedAt(review.getString("updatedAt"));

                        String replyText = review.getString("developerReply");
                        if (replyText != null && !replyText.isEmpty()) {
                            Comment.Reply reply = new Comment.Reply();
                            reply.setContent(replyText);

                            String replyDate = review.getString("developerReplyDate");
                            reply.setDate(replyDate != null ? replyDate : java.time.LocalDateTime.now().toString());

                            if (projectAuthor != null) {
                                reply.setUser(projectAuthor.getUsername());
                                reply.setUserAvatarUrl(projectAuthor.getAvatarUrl());
                            } else {
                                reply.setUser(authorName != null ? authorName : "Developer");
                            }
                            comment.setDeveloperReply(reply);
                        }

                        newComments.add(comment);
                    }

                    Update update = new Update();
                    update.set("comments", newComments);

                    mongoTemplate.updateFirst(
                            new Query(Criteria.where("_id").is(projectId)),
                            update,
                            "projects"
                    );

                    count++;
                    if (count % 100 == 0) {
                        logger.info("Migrated reviews for {} projects...", count);
                    }

                } catch (Exception e) {
                    logger.error("Failed to migrate project " + projectDoc.getString("_id"), e);
                }
            }

            logger.info("Migration finished. Processed {} projects. Skipped {}.", count, skipped);
        };
    }

    private User getAuthor(String authorId, String authorName) {
        if (authorId != null) {
            if (authorCache.containsKey(authorId)) return authorCache.get(authorId);
            return userRepository.findById(authorId).map(u -> {
                authorCache.put(authorId, u);
                return u;
            }).orElse(null);
        }
        if (authorName != null) {
            return userRepository.findByUsernameIgnoreCase(authorName).orElse(null);
        }
        return null;
    }
}