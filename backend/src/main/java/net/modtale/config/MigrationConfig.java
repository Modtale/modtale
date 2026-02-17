package net.modtale.config;

import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
public class MigrationConfig {

    @Bean
    public CommandLineRunner runMigration(MongoTemplate mongoTemplate) {
        return args -> {
            List<Document> users = mongoTemplate.find(new Query(), Document.class, "users");
            for (Document user : users) {
                boolean changed = false;

                String createdAtStr = user.getString("createdAt");
                if (createdAtStr != null) {
                    LocalDate createdAt = LocalDate.parse(createdAtStr);
                    if (createdAt.isBefore(LocalDate.of(2026, 1, 14))) {
                        List<String> badges = user.getList("badges", String.class);
                        if (badges == null) badges = new ArrayList<>();
                        if (!badges.contains("OG")) {
                            badges.add("OG");
                            user.put("badges", badges);
                            changed = true;
                        }
                    }
                }

                List<?> connectedAccounts = user.getList("connectedAccounts", Object.class);
                if (connectedAccounts != null && !connectedAccounts.isEmpty()) {
                    Boolean verified = user.getBoolean("emailVerified");
                    if (verified == null || !verified) {
                        user.put("emailVerified", true);
                        changed = true;
                    }
                }

                if (changed) {
                    mongoTemplate.save(user);
                }
            }

            List<Document> projects = mongoTemplate.find(new Query(), Document.class, "projects");
            for (Document project : projects) {
                boolean changed = false;

                String authorName = project.getString("author");
                String authorId = project.getString("authorId");
                Document authorUser = null;

                if (authorId == null && authorName != null) {
                    Query authorQuery = new Query(Criteria.where("username").regex("^" + authorName + "$", "i"));
                    authorUser = mongoTemplate.findOne(authorQuery, Document.class, "users");
                    if (authorUser != null) {
                        project.put("authorId", authorUser.getString("_id"));
                        changed = true;
                    }
                } else if (authorId != null) {
                    authorUser = mongoTemplate.findById(authorId, Document.class, "users");
                }

                List<Document> reviews = project.getList("reviews", Document.class);
                if (reviews != null && !reviews.isEmpty()) {
                    List<Document> comments = project.getList("comments", Document.class);
                    if (comments == null) comments = new ArrayList<>();

                    for (Document review : reviews) {
                        String reviewUser = review.getString("user");
                        String reviewContent = review.getString("comment");

                        boolean exists = comments.stream().anyMatch(c ->
                                reviewUser.equals(c.getString("user")) &&
                                        reviewContent.equals(c.getString("content")));

                        if (!exists) {
                            Document comment = new Document();
                            comment.put("_id", UUID.randomUUID().toString());
                            comment.put("user", reviewUser);
                            comment.put("userAvatarUrl", review.getString("userAvatarUrl"));
                            comment.put("content", reviewContent);
                            comment.put("date", review.getString("date"));
                            comment.put("updatedAt", review.getString("updatedAt"));

                            String devReplyContent = review.getString("developerReply");
                            if (devReplyContent != null) {
                                Document reply = new Document();
                                reply.put("content", devReplyContent);
                                reply.put("date", review.getString("developerReplyDate"));

                                if (authorUser != null) {
                                    reply.put("user", authorUser.getString("username"));
                                    reply.put("userAvatarUrl", authorUser.getString("avatarUrl"));
                                } else {
                                    reply.put("user", authorName);
                                }
                                comment.put("developerReply", reply);
                            }

                            comments.add(comment);
                            changed = true;
                        }
                    }

                    if (changed) {
                        project.put("comments", comments);
                    }
                }

                if (changed) {
                    mongoTemplate.save(project);
                }
            }
        };
    }
}