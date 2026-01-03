package net.modtale.model.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GitRepository {
    @JsonAlias({"full_name", "path_with_namespace"})
    private String name;

    @JsonAlias({"html_url", "web_url"})
    private String url;

    private String description;

    @JsonProperty("private")
    @JsonAlias("visibility")
    private boolean isPrivate;

    public void setVisibility(String visibility) {
        if ("private".equalsIgnoreCase(visibility) || "internal".equalsIgnoreCase(visibility)) {
            this.isPrivate = true;
        }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
}