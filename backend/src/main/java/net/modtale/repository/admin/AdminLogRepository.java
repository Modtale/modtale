package net.modtale.repository.admin;

import net.modtale.model.admin.AdminLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminLogRepository extends MongoRepository<AdminLog, String> {
}