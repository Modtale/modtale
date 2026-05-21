package net.modtale.model.dto;

import java.util.List;

public class ManifestInspectionResult {
    private String gameVersion;
    private List<ManifestDependencySuggestion> suggestions;

    public ManifestInspectionResult() {}

    public ManifestInspectionResult(String gameVersion, List<ManifestDependencySuggestion> suggestions) {
        this.gameVersion = gameVersion;
        this.suggestions = suggestions;
    }

    public String getGameVersion() { return gameVersion; }
    public void setGameVersion(String gameVersion) { this.gameVersion = gameVersion; }

    public List<ManifestDependencySuggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<ManifestDependencySuggestion> suggestions) { this.suggestions = suggestions; }
}
