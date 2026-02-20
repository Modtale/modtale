package net.modtale.repository.resources;

import net.modtale.model.resources.ModjamSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModjamSubmissionRepository extends MongoRepository<ModjamSubmission, String> {
    List<ModjamSubmission> findByJamId(String jamId);
    List<ModjamSubmission> findByJamIdAndSubmitterId(String jamId, String submitterId);
    List<ModjamSubmission> findByProjectId(String projectId);
}