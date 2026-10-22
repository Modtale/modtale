package net.modtale.service.jam;

import java.time.Instant;
import java.util.HashSet;
import net.modtale.model.project.Project;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.jam.ModjamSubmissionRepository;
import org.springframework.stereotype.Service;

@Service
public class ModjamEmbargoService {
    private final ModjamRepository jamRepository;
    private final ModjamSubmissionRepository submissionRepository;

    public ModjamEmbargoService(ModjamRepository jamRepository, ModjamSubmissionRepository submissionRepository) {
        this.jamRepository = jamRepository;
        this.submissionRepository = submissionRepository;
    }

    public boolean hasActiveEmbargo(Project project) {
        var ids = new HashSet<String>();
        if (project.getModjamIds() != null) ids.addAll(project.getModjamIds());
        if (project.getId() != null) {
            var submissions = submissionRepository.findByProjectId(project.getId());
            if (submissions != null) submissions.forEach(submission -> ids.add(submission.getJamId()));
        }
        Instant now = Instant.now();
        return ids.stream().filter(id -> id != null).anyMatch(id -> jamRepository.findById(id)
                .map(jam -> ModjamPhase.hidesEntries(jam, now)).orElse(false));
    }
}
