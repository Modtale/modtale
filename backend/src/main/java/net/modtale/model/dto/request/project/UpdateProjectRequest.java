package net.modtale.model.dto.request.project;

import java.util.List;
import java.util.Map;

public class UpdateProjectRequest {
    private String title;
    private String slug;
    private String description;
    private String about;
    private List<String> tags;
    private Map<String, String> links;
    private String repositoryUrl;
    private String license;
    private Boolean allowModpacks;
    private Boolean allowComments;
    private Boolean hmWikiEnabled;
    private String hmWikiSlug;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public Map<String, String> getLinks() { return links; }
    public void setLinks(Map<String, String> links) { this.links = links; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }
    public Boolean getAllowModpacks() { return allowModpacks; }
    public void setAllowModpacks(Boolean allowModpacks) { this.allowModpacks = allowModpacks; }
    public Boolean getAllowComments() { return allowComments; }
    public void setAllowComments(Boolean allowComments) { this.allowComments = allowComments; }
    public Boolean getHmWikiEnabled() { return hmWikiEnabled; }
    public void setHmWikiEnabled(Boolean hmWikiEnabled) { this.hmWikiEnabled = hmWikiEnabled; }
    public String getHmWikiSlug() { return hmWikiSlug; }
    public void setHmWikiSlug(String hmWikiSlug) { this.hmWikiSlug = hmWikiSlug; }
}
