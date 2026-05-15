package net.modtale.service.project;

import net.modtale.model.project.ProjectClassification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ValidationService {

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "Adventure", "RPG", "Sci-Fi", "Fantasy", "Survival", "Magic", "Tech", "Exploration",
            "Minigame", "PvP", "Parkour", "Hardcore", "Skyblock", "Puzzle", "Quests", "Mobs",
            "Economy", "Protection", "Admin Tools", "Chat", "Anti-Cheat", "Performance", "NPCs",
            "Library", "API", "Mechanics", "World Gen", "Recipes", "Loot Tables", "Functions",
            "Decoration", "Vanilla+", "Kitchen Sink", "City", "Landscape", "Spawn", "Lobby",
            "Medieval", "Modern", "Futuristic", "Models", "Textures", "Animations", "Particles"
    );

    private static final Map<String, String> CANONICAL_TAG_MAP = ALLOWED_TAGS.stream()
            .collect(Collectors.toMap(String::toLowerCase, Function.identity()));

    private static final Set<String> ALLOWED_GAME_VERSIONS = Set.of(
            "2026.01.13-dcad8778f", "2026.01.17-4b0f30090", "2026.01.24-6e2d4fc36", "2026.01.28-87d03be09", "2026.02.06-aa1b071c2", "2026.02.17-255364b8e", "2026.02.19-1a311a592", "2026.03.26-89796e57b"
    );

    private static final Pattern STRICT_VERSION_PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");
    private static final Pattern REPO_URL_PATTERN = Pattern.compile("^https:\\/\\/(github\\.com|gitlab\\.com|codeberg\\.org)\\/[\\w.-]+\\/[\\w.-]+$");
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$");

    public List<String> validateTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return new ArrayList<>();
        List<String> normalized = new ArrayList<>();
        List<String> invalidTags = new ArrayList<>();

        for (String tag : tags) {
            String canonical = CANONICAL_TAG_MAP.get(tag.toLowerCase());
            if (canonical != null) normalized.add(canonical);
            else invalidTags.add(tag);
        }

        if (!invalidTags.isEmpty()) {
            throw new IllegalArgumentException("Invalid tags detected: " + String.join(", ", invalidTags));
        }
        return normalized;
    }

    public void validateVersionNumber(String version) {
        if (version == null || !STRICT_VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException("Version number must follow SemVer format (e.g., 1.0.0, 1.0.0-rc.1, 1.0.0+build).");
        }
    }

    public void validateSlug(String slug) {
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("Invalid URL Slug. Must be 3-50 characters, lowercase alphanumeric with dashes, and cannot start or end with a dash.");
        }
    }

    public void validateRepositoryUrl(String url) {
        if (url != null && !url.isEmpty() && !REPO_URL_PATTERN.matcher(url).matches()) {
            throw new IllegalArgumentException("Invalid Repository URL. Must be a valid HTTPS link to GitHub or GitLab.");
        }
    }

    public List<String> getAllowedTags() {
        return ALLOWED_TAGS.stream().sorted().collect(Collectors.toList());
    }

    public List<String> getAllowedGameVersions() {
        return ALLOWED_GAME_VERSIONS.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    public List<String> getAllowedClassifications() {
        return Arrays.stream(ProjectClassification.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }
}