package net.modtale.config;

import net.modtale.model.resources.Mod;
import net.modtale.model.user.User;
import net.modtale.repository.resources.ModRepository;
import net.modtale.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

@Configuration
public class DatabaseMigrationConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrationConfig.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModRepository modRepository;

    @Bean
    public CommandLineRunner migrateAuthorIds() {
        return args -> {
            logger.info("Starting migration: linking author IDs to projects...");

            Query query = new Query(Criteria.where("authorId").exists(false).and("author").exists(true));
            List<Mod> modsToMigrate = mongoTemplate.find(query, Mod.class);

            if (modsToMigrate.isEmpty()) {
                logger.info("No projects require migration.");
                return;
            }

            int migrated = 0;
            int failed = 0;

            for (Mod mod : modsToMigrate) {
                if (mod.getAuthor() != null) {
                    try {
                        Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(mod.getAuthor());
                        if (userOpt.isPresent()) {
                            mod.setAuthorId(userOpt.get().getId());
                            modRepository.save(mod);
                            migrated++;
                        } else {
                            logger.warn("Could not find user for project {}: author '{}'", mod.getId(), mod.getAuthor());
                            failed++;
                        }
                    } catch (Exception e) {
                        logger.error("Failed to migrate project {}", mod.getId(), e);
                        failed++;
                    }
                }
            }

            logger.info("Migration completed. Migrated: {}, Failed/Skipped: {}", migrated, failed);
        };
    }
}