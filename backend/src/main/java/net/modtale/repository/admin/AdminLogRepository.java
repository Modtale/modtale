package net.modtale.repository.admin;

import net.modtale.model.admin.AdminLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface AdminLogRepository extends MongoRepository<AdminLog, String> {
    @Query("{ '$and': [ " +
            "{ '$or': [ { 'adminUsername': { $regex: ?0, $options: 'i' } }, { 'action': { $regex: ?0, $options: 'i' } }, { 'targetId': { $regex: ?0, $options: 'i' } }, { 'details': { $regex: ?0, $options: 'i' } } ] }, " +
            "{ 'action': { $regex: ?1, $options: 'i' } }, " +
            "{ 'targetType': { $regex: ?2, $options: 'i' } } " +
            "] }")
    Page<AdminLog> findWithFilters(String query, String action, String targetType, Pageable pageable);
}