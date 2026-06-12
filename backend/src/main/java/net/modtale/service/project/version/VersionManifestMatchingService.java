package net.modtale.service.project.version;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.modtale.model.dto.project.ManifestDependencySuggestion;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.security.validation.FileValidationService.ManifestDependency;
import org.springframework.stereotype.Service;

@Service
public class VersionManifestMatchingService {

    private static final Pattern SEMVER_TOKEN_PATTERN =
            Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?");
    private static final Pattern RANGE_PART_PATTERN =
            Pattern.compile("(\\^|>=|<=|>|<|=)?\\s*((?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?)");

    public String resolveManifestGameVersion(String serverVersionRaw, List<String> allowedVersions) {
        if (serverVersionRaw == null || serverVersionRaw.isBlank() || allowedVersions == null || allowedVersions.isEmpty()) {
            return null;
        }

        String raw = serverVersionRaw.trim();
        if (allowedVersions.contains(raw)) {
            return raw;
        }

        List<RangeConstraint> constraints = parseRangeConstraints(raw);
        if (constraints.isEmpty()) {
            return null;
        }

        String best = null;
        SemVer bestParsed = null;
        for (String candidate : allowedVersions) {
            SemVer parsed = SemVer.parse(candidate);
            if (parsed == null) {
                continue;
            }

            boolean matchesAll = true;
            for (RangeConstraint constraint : constraints) {
                if (!constraint.matches(parsed)) {
                    matchesAll = false;
                    break;
                }
            }
            if (!matchesAll) {
                continue;
            }
            if (best == null) {
                best = candidate;
                bestParsed = parsed;
                continue;
            }

            boolean preferSmaller = constraints.stream().noneMatch(RangeConstraint::hasUpperBound);
            int comparison = parsed.compareTo(bestParsed);
            if ((preferSmaller && comparison < 0) || (!preferSmaller && comparison > 0)) {
                best = candidate;
                bestParsed = parsed;
            }
        }

        if (best != null) {
            return best;
        }

        String targetPrefix = constraints.getFirst().target().toString().toLowerCase(Locale.ROOT);
        return allowedVersions.stream()
                .filter(version -> version != null && version.toLowerCase(Locale.ROOT).startsWith(targetPrefix))
                .findFirst()
                .orElse(null);
    }

    public List<ManifestDependencySuggestion> suggestDependencies(
            List<ManifestDependency> dependencies,
            List<Project> candidates
    ) {
        List<ManifestDependencySuggestion> suggestions = new ArrayList<>();
        for (ManifestDependency dependency : dependencies) {
            Project bestProject = null;
            int bestScore = 0;

            for (Project candidate : candidates) {
                int score = scoreDependencyMatch(dependency, candidate);
                if (score > bestScore) {
                    bestScore = score;
                    bestProject = candidate;
                }
            }

            if (bestProject != null && bestScore >= 80) {
                ProjectVersion version = selectSuggestedVersion(bestProject, dependency.getVersion());
                if (version != null) {
                    suggestions.add(new ManifestDependencySuggestion(
                            dependency.getKey(),
                            dependency.getVersion(),
                            bestProject.getId(),
                            bestProject.getTitle(),
                            version.getVersionNumber(),
                            dependency.isOptional(),
                            bestScore
                    ));
                }
            }
        }
        return suggestions;
    }

    private List<RangeConstraint> parseRangeConstraints(String raw) {
        List<RangeConstraint> constraints = new ArrayList<>();
        Matcher matcher = RANGE_PART_PATTERN.matcher(raw);
        while (matcher.find()) {
            String operator = matcher.group(1);
            String token = matcher.group(2);
            SemVer target = SemVer.parse(token);
            if (target == null) {
                continue;
            }
            constraints.add(new RangeConstraint(operator == null ? "=" : operator.trim(), target));
        }
        return constraints;
    }

    private int scoreDependencyMatch(ManifestDependency dependency, Project candidate) {
        String dependencyName = normalizeDependencyName(dependency.getNamePart());
        String dependencyKey = normalizeDependencyName(dependency.getKey());
        String title = normalizeDependencyName(candidate.getTitle());
        String slug = normalizeDependencyName(candidate.getSlug());

        if (dependencyName.isEmpty()) {
            return 0;
        }
        if (!title.isEmpty() && dependencyName.equals(title)) {
            return 100;
        }
        if (!slug.isEmpty() && dependencyName.equals(slug)) {
            return 95;
        }
        if (!dependencyKey.isEmpty()
                && ((!title.isEmpty() && dependencyKey.equals(title)) || (!slug.isEmpty() && dependencyKey.equals(slug)))) {
            return 90;
        }
        if (isStrongContainedMatch(dependencyName, title)) {
            return 85;
        }
        if (isStrongContainedMatch(dependencyName, slug)) {
            return 82;
        }
        if (isStrongFuzzyMatch(dependencyName, title) || isStrongFuzzyMatch(dependencyName, slug)) {
            return 80;
        }
        return 0;
    }

    private boolean isStrongContainedMatch(String dependencyName, String candidateName) {
        if (dependencyName.isEmpty() || candidateName.isEmpty()) {
            return false;
        }
        int shorter = Math.min(dependencyName.length(), candidateName.length());
        int longer = Math.max(dependencyName.length(), candidateName.length());
        if (shorter < 6) {
            return false;
        }
        if ((double) shorter / longer < 0.75) {
            return false;
        }
        return candidateName.contains(dependencyName) || dependencyName.contains(candidateName);
    }

    private boolean isStrongFuzzyMatch(String dependencyName, String candidateName) {
        if (dependencyName.isEmpty() || candidateName.isEmpty()) {
            return false;
        }
        int longer = Math.max(dependencyName.length(), candidateName.length());
        if (longer < 6) {
            return false;
        }
        int distance = levenshtein(dependencyName, candidateName);
        return distance <= 2 && ((double) distance / longer) <= 0.2;
    }

    private ProjectVersion selectSuggestedVersion(Project project, String requestedVersion) {
        if (project.getVersions() == null || project.getVersions().isEmpty()) {
            return null;
        }

        String exactVersion = requestedVersion == null ? "" : requestedVersion
                .replace(">=", "")
                .replace("<=", "")
                .replace(">", "")
                .replace("<", "")
                .replace("=", "")
                .trim();
        if (!exactVersion.isEmpty() && !"*".equals(exactVersion)) {
            Optional<ProjectVersion> exact = project.getVersions().stream()
                    .filter(version -> version.getVersionNumber() != null && version.getVersionNumber().equalsIgnoreCase(exactVersion))
                    .findFirst();
            if (exact.isPresent()) {
                return exact.get();
            }
        }

        return project.getVersions().stream()
                .max(Comparator.comparing(ProjectVersion::getReleaseDate, Comparator.nullsLast(String::compareTo)))
                .orElse(project.getVersions().getFirst());
    }

    private String normalizeDependencyName(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private int levenshtein(String left, String right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }

        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }
            int[] tmp = previous;
            previous = current;
            current = tmp;
        }
        return previous[right.length()];
    }

    private record RangeConstraint(String operator, SemVer target, SemVer caretUpperBound) {
        private RangeConstraint(String operator, SemVer target) {
            this(operator == null || operator.isBlank() ? "=" : operator, target,
                    "^".equals(operator == null || operator.isBlank() ? "=" : operator) ? target.caretUpperBound() : null);
        }

        boolean hasUpperBound() {
            return "<".equals(operator) || "<=".equals(operator) || "^".equals(operator);
        }

        boolean matches(SemVer candidate) {
            int comparison = candidate.compareTo(target);
            return switch (operator) {
                case ">" -> comparison > 0;
                case ">=" -> comparison >= 0;
                case "<" -> comparison < 0;
                case "<=" -> comparison <= 0;
                case "^" -> comparison >= 0 && candidate.compareTo(caretUpperBound) < 0;
                default -> comparison == 0;
            };
        }
    }

    private record SemVer(int major, int minor, int patch, String preRelease) implements Comparable<SemVer> {
        static SemVer parse(String value) {
            if (value == null) {
                return null;
            }
            Matcher matcher = SEMVER_TOKEN_PATTERN.matcher(value.trim());
            if (!matcher.matches()) {
                return null;
            }
            try {
                return new SemVer(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)),
                        matcher.group(4)
                );
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        @Override
        public int compareTo(SemVer other) {
            int majorComparison = Integer.compare(major, other.major);
            if (majorComparison != 0) {
                return majorComparison;
            }
            int minorComparison = Integer.compare(minor, other.minor);
            if (minorComparison != 0) {
                return minorComparison;
            }
            int patchComparison = Integer.compare(patch, other.patch);
            if (patchComparison != 0) {
                return patchComparison;
            }
            if (preRelease == null && other.preRelease == null) {
                return 0;
            }
            if (preRelease == null) {
                return 1;
            }
            if (other.preRelease == null) {
                return -1;
            }
            return preRelease.compareTo(other.preRelease);
        }

        SemVer caretUpperBound() {
            if (major > 0) {
                return new SemVer(major + 1, 0, 0, null);
            }
            if (minor > 0) {
                return new SemVer(0, minor + 1, 0, null);
            }
            return new SemVer(0, 0, patch + 1, null);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch + (preRelease != null ? "-" + preRelease : "");
        }
    }
}
