package net.modtale.model.dto.project;

import java.util.List;

public class ManifestInspectionResult {
    private String gameVersion;
    private String modVersion;
    private List<ManifestDependencySuggestion> suggestions;

    public ManifestInspectionResult() {}

    public ManifestInspectionResult(String gameVersion, String modVersion, List<ManifestDependencySuggestion> suggestions) {
        this.gameVersion = gameVersion;
        this.modVersion = modVersion;
        this.suggestions = suggestions;
    }

    public String getGameVersion() { return gameVersion; }
    public void setGameVersion(String gameVersion) { this.gameVersion = gameVersion; }

    public String getModVersion() { return modVersion; }
    public void setModVersion(String modVersion) { this.modVersion = modVersion; }

    public List<ManifestDependencySuggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<ManifestDependencySuggestion> suggestions) { this.suggestions = suggestions; }
}
