package net.modtale.repository;

import net.modtale.model.AdminLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminLogRepository extends MongoRepository<AdminLog, String> {
}