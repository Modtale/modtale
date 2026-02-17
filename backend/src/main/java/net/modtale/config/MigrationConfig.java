package net.modtale.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class MigrationConfig {

    private static final Logger logger = LoggerFactory.getLogger(MigrationConfig.class);

    @Bean
    public CommandLineRunner runMigration(MongoTemplate mongoTemplate) {
        return args -> {
            logger.info("Starting database migration script...");

            long totalUsers = mongoTemplate.count(new Query(), "users");
            logger.info("Found {} users to process.", totalUsers);

            List<Document> users = mongoTemplate.find(new Query(), Document.class, "users");
            AtomicInteger userCount = new AtomicInteger(0);
            AtomicInteger usersModified = new AtomicInteger(0);

            for (Document user : users) {
                try {
                    boolean changed = false;

                    String createdAtStr = user.getString("createdAt");
                    if (createdAtStr != null) {
                        try {
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
                        } catch (Exception e) {
                            logger.warn("Could not parse date for user {}: {}", user.getString("username"), createdAtStr);
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
                        mongoTemplate.save(user, "users");
                        usersModified.incrementAndGet();
                    }

                    if (userCount.incrementAndGet() % 50 == 0) {
                        logger.info("Processed {}/{} users...", userCount.get(), totalUsers);
                    }

                } catch (Exception e) {
                    logger.error("Failed to process user: " + user.get("_id"), e);
                }
            }
            logger.info("User migration finished. Modified {} users.", usersModified.get());

            long totalProjects = mongoTemplate.count(new Query(), "projects");
            logger.info("Found {} projects to process.", totalProjects);

            List<Document> projects = mongoTemplate.find(new Query(), Document.class, "projects");
            AtomicInteger projectCount = new AtomicInteger(0);
            AtomicInteger projectsModified = new AtomicInteger(0);

            for (Document project : projects) {
                try {
                    boolean changed = false;
                    String projectTitle = project.getString("title");

                    String authorName = project.getString("author");

                    String authorId = null;
                    Object authorIdObj = project.get("authorId");
                    if (authorIdObj != null) {
                        authorId = authorIdObj.toString();
                    }

                    Document authorUser = null;

                    if (authorId == null && authorName != null) {
                        Query authorQuery = new Query(Criteria.where("username").regex("^" + authorName + "$", "i"));
                        authorUser = mongoTemplate.findOne(authorQuery, Document.class, "users");
                        if (authorUser != null) {
                            project.put("authorId", authorUser.get("_id").toString());
                            changed = true;
                        } else {
                            logger.warn("Project '{}' has author '{}' but no matching user found.", projectTitle, authorName);
                        }
                    } else if (authorId != null) {
                        authorUser = mongoTemplate.findById(authorId, Document.class, "users");
                    }

                    List<Document> reviews = project.getList("reviews", Document.class);
                    if (reviews != null && !reviews.isEmpty()) {
                        List<Document> comments = project.getList("comments", Document.class);
                        if (comments == null) comments = new ArrayList<>();

                        int newCommentsAdded = 0;
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
                                        reply.put("user", authorName != null ? authorName : "Developer");
                                    }
                                    comment.put("developerReply", reply);
                                }

                                comments.add(comment);
                                newCommentsAdded++;
                            }
                        }

                        if (newCommentsAdded > 0) {
                            project.put("comments", comments);
                            changed = true;
                        }
                    }

                    if (changed) {
                        mongoTemplate.save(project, "projects");
                        projectsModified.incrementAndGet();
                    }

                    if (projectCount.incrementAndGet() % 50 == 0) {
                        logger.info("Processed {}/{} projects...", projectCount.get(), totalProjects);
                    }

                } catch (Exception e) {
                    logger.error("Failed to process project: " + project.get("_id"), e);
                }
            }

            logger.info("Migration completed. Users modified: {}, Projects modified: {}", usersModified.get(), projectsModified.get());
        };
    }
}