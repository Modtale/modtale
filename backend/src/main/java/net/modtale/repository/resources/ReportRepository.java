package net.modtale.repository.resources;

import net.modtale.model.resources.Report;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ReportRepository extends MongoRepository<Report, String> {
    List<Report> findByStatus(Report.ReportStatus status);
    List<Report> findByProjectId(String projectId);
}