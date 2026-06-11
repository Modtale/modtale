package net.modtale.service.storage;

import net.modtale.config.properties.AppLimitProperties;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.ProjectService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class DownloadService {

    private final DownloadRateLimitService rateLimitService;
    private final ModpackArchiveService modpackArchiveService;
    private final BundlePackagingService bundlePackagingService;

    public DownloadService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            StorageService storageService,
            AppLimitProperties limitProperties
    ) {
        DownloadArchiveSupport archiveSupport = new DownloadArchiveSupport(projectService, storageService);
        this.rateLimitService = new DownloadRateLimitService(limitProperties.modpackGenPerHour());
        this.modpackArchiveService = new ModpackArchiveService(projectRepository, archiveSupport);
        this.bundlePackagingService = new BundlePackagingService(archiveSupport);
    }

    public byte[] generateModpackZip(Project pack, ProjectVersion version, User user) throws IOException {
        rateLimitService.consumeModpackGeneration(user);
        return modpackArchiveService.generateModpackZip(pack, version);
    }

    public byte[] generateBundleZip(Project mainProject, ProjectVersion mainVersion, List<String> selectedDependencies, User user) throws IOException {
        rateLimitService.consumeBundleGeneration(user);
        return bundlePackagingService.generateBundleZip(mainProject, mainVersion, selectedDependencies);
    }
}
