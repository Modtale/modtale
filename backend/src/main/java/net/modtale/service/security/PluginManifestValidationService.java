package net.modtale.service.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.modtale.exception.InvalidProjectRequestException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class PluginManifestValidationService {

    private final ObjectMapper objectMapper;

    public PluginManifestValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FileValidationService.ManifestInspection validatePluginManifest(InputStream inputStream) {
        try {
            JsonNode root = objectMapper.readTree(inputStream);

            String[] requiredFields = {"Group", "Name", "Version", "ServerVersion", "Main"};
            for (String field : requiredFields) {
                if (!root.has(field) || root.get(field).asText().isEmpty()) {
                    throw new InvalidProjectRequestException("Plugin manifest.json is missing required field: " + field);
                }
            }

            if (!root.has("Authors") || !root.get("Authors").isArray() || root.get("Authors").isEmpty()) {
                throw new InvalidProjectRequestException("Plugin manifest.json must contain at least one Author.");
            }

            for (JsonNode author : root.get("Authors")) {
                if (!author.has("Name") || author.get("Name").asText().isBlank()) {
                    throw new InvalidProjectRequestException("Plugin manifest.json Authors entries must include Name.");
                }
            }

            List<FileValidationService.ManifestDependency> dependencies = new ArrayList<>();
            readManifestDependencies(root.get("Dependencies"), false, dependencies);
            readManifestDependencies(root.get("OptionalDependencies"), true, dependencies);

            return new FileValidationService.ManifestInspection(
                    root.get("Group").asText(),
                    root.get("Name").asText(),
                    root.get("Version").asText(),
                    root.get("ServerVersion").asText(),
                    dependencies
            );
        } catch (IOException e) {
            throw new InvalidProjectRequestException("Failed to parse manifest.json. Ensure it is valid JSON.");
        }
    }

    private void readManifestDependencies(
            JsonNode node,
            boolean optional,
            List<FileValidationService.ManifestDependency> dependencies
    ) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (!node.isObject()) {
            throw new InvalidProjectRequestException((optional ? "OptionalDependencies" : "Dependencies") + " must be a JSON object.");
        }

        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                return;
            }
            if (key.regionMatches(true, 0, "Hytale:", 0, "Hytale:".length())) {
                return;
            }

            String version = entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue().toString();
            dependencies.add(new FileValidationService.ManifestDependency(key, version, optional));
        });
    }
}
