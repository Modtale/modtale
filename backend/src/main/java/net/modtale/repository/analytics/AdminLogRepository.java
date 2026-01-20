package net.modtale.repository.analytics;

import net.modtale.model.analytics.AdminLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminLogRepository extends MongoRepository<AdminLog, String> {
}