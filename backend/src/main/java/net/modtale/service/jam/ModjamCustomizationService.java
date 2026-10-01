package net.modtale.service.jam;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.modtale.model.jam.Modjam;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.project.catalog.GameVersionService;
import org.springframework.stereotype.Service;

@Service
public class ModjamCustomizationService {
    private static final Set<String> SELECTORS = Set.of("h1", "h2", "h3", "p", "a", "blockquote", "ul", "ol", "li", "code", "pre", "table", "th", "td", "hr", "strong", "em");
    private static final Set<String> PROPERTIES = Set.of("background-color", "color", "border-color", "border-radius", "font-size", "font-weight", "line-height", "letter-spacing", "padding", "margin", "text-align", "text-decoration");
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
    private static final Pattern UNSAFE_VALUE = Pattern.compile("\\b(?:url|expression|var|attr|env)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private final GameVersionService gameVersionService;

    public ModjamCustomizationService(GameVersionService gameVersionService) {
        this.gameVersionService = gameVersionService;
    }

    public void validate(Modjam jam) {
        validateCss(jam.getCustomCss());
        if (jam.getSlug() == null || !jam.getSlug().matches("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$")) {
            throw new IllegalArgumentException("Jam URL must be 3–50 lowercase letters, numbers or dashes, without a leading or trailing dash.");
        }
        if (jam.getTitle() == null || jam.getTitle().trim().length() < 5 || jam.getTitle().length() > 150) {
            throw new IllegalArgumentException("Jam title must be 5–150 characters.");
        }
        if (!Set.of("DRAFT", "UPCOMING", "ACTIVE", "VOTING", "AWAITING_WINNERS", "COMPLETED").contains(jam.getStatus())) {
            throw new IllegalArgumentException("Invalid jam status.");
        }
        if (!"DRAFT".equals(jam.getStatus())) {
            if (jam.getDescription() == null || jam.getDescription().trim().length() < 10
                    || jam.getStartDate() == null || jam.getEndDate() == null || jam.getVotingEndDate() == null
                    || !jam.getStartDate().isBefore(jam.getEndDate()) || !jam.getEndDate().isBefore(jam.getVotingEndDate())
                    || jam.getCategories() == null || jam.getCategories().isEmpty()) {
                throw new IllegalArgumentException("Published jams need a description, ordered start/submission/voting dates and scoring criteria.");
            }
        }
        Set<String> categoryIds = new HashSet<>();
        if (jam.getCategories() != null) {
            if (jam.getCategories().size() > 20) throw new IllegalArgumentException("A jam supports up to 20 scoring criteria.");
            for (Modjam.Category category : jam.getCategories()) {
                if (category == null || category.getName() == null || category.getName().isBlank() || category.getName().length() > 100
                        || category.getMaxScore() < 1 || category.getMaxScore() > 100) {
                    throw new IllegalArgumentException("Scoring criteria need a name and a maximum score between 1 and 100.");
                }
                if (category.getId() != null && !category.getId().isBlank() && !categoryIds.add(category.getId())) {
                    throw new IllegalArgumentException("Scoring criterion IDs must be unique.");
                }
            }
        }
        validateVersionRestriction(jam.getRestrictions());
    }

    public void validateVersionRestriction(Modjam.Restrictions restrictions) {
        if (restrictions == null) return;
        List<String> exact = restrictions.getAllowedGameVersions();
        String minimum = restrictions.getMinimumGameVersion();
        String maximum = restrictions.getMaximumGameVersion();
        boolean range = present(minimum) || present(maximum);
        if ((exact == null || exact.isEmpty()) && !range) return;
        List<String> catalog = gameVersionService.getCatalog().allVersions();
        if (exact != null && !exact.isEmpty()) {
            if (range) throw new IllegalArgumentException("Choose specific game versions or a range, not both.");
            if (exact.size() > 100 || exact.stream().anyMatch(version -> version == null || !catalog.contains(version))) {
                throw new IllegalArgumentException("Choose supported game versions from the catalog.");
            }
        }
        if (present(minimum) && !catalog.contains(minimum) || present(maximum) && !catalog.contains(maximum)) {
            throw new IllegalArgumentException("Game version range endpoints must exist in the catalog.");
        }
        if (present(minimum) && present(maximum) && catalog.indexOf(minimum) < catalog.indexOf(maximum)) {
            throw new IllegalArgumentException("Minimum game version must not be newer than maximum game version.");
        }
    }

    public void validateProjectVersions(Modjam.Restrictions restrictions, Project project) {
        if (restrictions == null) return;
        List<String> exact = restrictions.getAllowedGameVersions();
        boolean range = present(restrictions.getMinimumGameVersion()) || present(restrictions.getMaximumGameVersion());
        if ((exact == null || exact.isEmpty()) && !range) return;
        validateVersionRestriction(restrictions);
        List<String> catalog = gameVersionService.getCatalog().allVersions();
        boolean reviewing = project.getStatus() == ProjectStatus.DRAFT || project.getStatus() == ProjectStatus.PENDING;
        boolean compatible = project.getVersions() != null && project.getVersions().stream()
                .filter(version -> version != null && present(version.getFileUrl()))
                .filter(version -> version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED
                        || reviewing && version.getReviewStatus() == ProjectVersion.ReviewStatus.PENDING)
                .filter(version -> version.getGameVersions() != null)
                .flatMap(version -> version.getGameVersions().stream())
                .anyMatch(version -> range ? inRange(version, restrictions, catalog) : exact.contains(version));
        if (!compatible) throw new IllegalArgumentException("Project needs an eligible uploaded release supporting a required game version.");
    }

    private boolean inRange(String version, Modjam.Restrictions restrictions, List<String> catalog) {
        int index = catalog.indexOf(version);
        return index >= 0
                && (!present(restrictions.getMinimumGameVersion()) || index <= catalog.indexOf(restrictions.getMinimumGameVersion()))
                && (!present(restrictions.getMaximumGameVersion()) || index >= catalog.indexOf(restrictions.getMaximumGameVersion()));
    }

    public static void validateCss(String css) {
        if (css == null || css.isBlank()) return;
        if (css.length() > 10000 || Pattern.compile("[@\\\\<>\"'!]").matcher(css).find()) {
            throw new IllegalArgumentException("Custom CSS cannot contain imports, escapes or unsafe syntax.");
        }
        String input = css.replaceAll("(?s)/\\*.*?\\*/", "").trim();
        var matcher = RULE.matcher(input);
        int end = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            if (!input.substring(end, matcher.start()).isBlank()) throw invalidCss();
            for (String selector : matcher.group(1).split(",", -1)) {
                if (!SELECTORS.contains(selector.trim())) throw invalidCss();
            }
            for (String declaration : matcher.group(2).split(";")) {
                if (declaration.isBlank()) continue;
                String[] parts = declaration.split(":", -1);
                if (parts.length != 2 || !PROPERTIES.contains(parts[0].trim())) throw invalidCss();
                String value = parts[1].trim();
                if (value.isEmpty() || !value.matches("[a-zA-Z0-9#.%(),\\s-]+") || UNSAFE_VALUE.matcher(value).find()
                        || (value.contains("(") || value.contains(")")) && !value.matches("(?i)(rgb|rgba|hsl|hsla)\\([\\d.%\\s,/-]+\\)")) throw invalidCss();
            }
            end = matcher.end();
        }
        if (!found || !input.substring(end).isBlank()) throw invalidCss();
    }

    private static IllegalArgumentException invalidCss() {
        return new IllegalArgumentException("Use simple Markdown element selectors and typography/color/spacing declarations in custom CSS.");
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
