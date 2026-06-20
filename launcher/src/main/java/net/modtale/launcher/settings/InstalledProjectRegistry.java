package net.modtale.launcher.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.modtale.launcher.api.ModtaleApiException;
import net.modtale.launcher.model.install.InstalledProject;

final class InstalledProjectRegistry {

    private static final String REGISTRY_FILE = "installed-projects.json";

    private final Path registryPath;
    private final ObjectMapper mapper;

    InstalledProjectRegistry(Path launcherDirectory, ObjectMapper mapper) {
        Path directory = launcherDirectory == null ? Path.of(".") : launcherDirectory;
        this.registryPath = directory.resolve(REGISTRY_FILE);
        this.mapper = mapper;
    }

    List<InstalledProject> merge(List<InstalledProject> projects) {
        Map<String, InstalledProject> merged = new LinkedHashMap<>();
        for (InstalledProject project : validProjects(projects)) {
            merged.put(project.projectId(), project);
        }
        for (InstalledProject project : load()) {
            if (hasAnyRecordedFile(project)) {
                merged.putIfAbsent(project.projectId(), project);
            }
        }
        return List.copyOf(merged.values());
    }

    void save(List<InstalledProject> projects) {
        List<InstalledProject> records = validProjects(projects);
        try {
            Files.createDirectories(registryPath.getParent());
            mapper.writeValue(registryPath.toFile(), new RegistryFile(records));
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not save installed project registry to " + registryPath, ex);
        }
    }

    void remove(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        List<InstalledProject> remaining = load().stream()
                .filter(project -> !projectId.trim().equals(project.projectId()))
                .toList();
        save(remaining);
    }

    private List<InstalledProject> load() {
        if (!Files.isRegularFile(registryPath)) {
            return List.of();
        }
        try {
            if (Files.size(registryPath) == 0 || Files.readString(registryPath).isBlank()) {
                return List.of();
            }
            RegistryFile registry = mapper.readValue(registryPath.toFile(), RegistryFile.class);
            return validProjects(registry.installedProjects());
        } catch (IOException ex) {
            try {
                return validProjects(mapper.readValue(registryPath.toFile(), new TypeReference<List<InstalledProject>>() {
                }));
            } catch (IOException ignored) {
                throw new ModtaleApiException("Could not load installed project registry from " + registryPath, ex);
            }
        }
    }

    private static List<InstalledProject> validProjects(List<InstalledProject> projects) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }
        List<InstalledProject> valid = new ArrayList<>();
        for (InstalledProject project : projects) {
            if (project != null && project.projectId() != null && !project.projectId().isBlank()) {
                valid.add(project);
            }
        }
        return valid;
    }

    private static boolean hasAnyRecordedFile(InstalledProject project) {
        if (project.files().isEmpty()) {
            return true;
        }
        return project.files().stream()
                .filter(file -> file != null && !file.isBlank())
                .map(Path::of)
                .anyMatch(Files::exists);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RegistryFile(List<InstalledProject> installedProjects) {
        private RegistryFile {
            installedProjects = installedProjects == null ? List.of() : List.copyOf(installedProjects);
        }
    }
}
