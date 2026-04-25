package net.modtale.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CommentMigrationRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(CommentMigrationRunner.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    private final Map<String, String> usernameToIdCache = new HashMap<>();

    @Override
    public void run(String... args) {
        logger.info("Starting non-destructive comment migration...");

        MongoCollection<Document> collection = mongoTemplate.getCollection("projects");

        Document query = new Document("comments", new Document("$exists", true).append("$not", new Document("$size", 0)));

        int processedCount = 0;
        int updatedCount = 0;

        try (MongoCursor<Document> cursor = collection.find(query).iterator()) {
            while (cursor.hasNext()) {
                Document projectDoc = cursor.next();
                boolean modified = false;

                List<Document> comments = projectDoc.getList("comments", Document.class);
                if (comments != null) {
                    for (Document comment : comments) {
                        // Migrate main comment
                        if (!comment.containsKey("userId") && comment.containsKey("user")) {
                            String username = comment.getString("user");
                            String resolvedId = resolveUserId(username);

                            if (resolvedId != null) {
                                comment.put("userId", resolvedId);
                                modified = true;
                            }
                        }

                        // Migrate developer reply if present
                        Document reply = (Document) comment.get("developerReply");
                        if (reply != null && !reply.containsKey("userId") && reply.containsKey("user")) {
                            String replyUsername = reply.getString("user");
                            String resolvedReplyId = resolveUserId(replyUsername);

                            if (resolvedReplyId != null) {
                                reply.put("userId", resolvedReplyId);
                                modified = true;
                            }
                        }
                    }
                }

                // If we altered any comments in this project, perform a raw update
                if (modified) {
                    collection.updateOne(
                            new Document("_id", projectDoc.get("_id")),
                            new Document("$set", new Document("comments", comments))
                    );
                    updatedCount++;
                }

                processedCount++;

                if (processedCount % 50 == 0) {
                    logger.info("Migration progress: {} projects processed, {} projects updated so far...", processedCount, updatedCount);
                }
            }
        } catch (Exception e) {
            logger.error("Error occurred during comment migration", e);
        }

        logger.info("Comment migration complete");
        logger.info("Total projects processed: {}", processedCount);
        logger.info("Total projects updated: {}", updatedCount);
    }

    private String resolveUserId(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        if (usernameToIdCache.containsKey(username)) {
            return usernameToIdCache.get(username);
        }

        Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(username);
        String id = userOpt.map(User::getId).orElse(null);

        usernameToIdCache.put(username, id);
        return id;
    }
}