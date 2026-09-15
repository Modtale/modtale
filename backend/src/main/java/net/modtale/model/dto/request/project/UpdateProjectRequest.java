package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public class UpdateProjectRequest {
    @Size(max = 100, message = "Project titles must be 100 characters or fewer.")
    private String title;

    @Pattern(
            regexp = "^$|^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$",
            message = "Project slugs must be 3-50 lowercase characters and may include dashes."
    )
    private String slug;

    @Size(max = 250, message = "The short summary cannot exceed 250 characters.")
    private String description;

    @Size(max = 50000, message = "The full description cannot exceed 50,000 characters.")
    private String about;
    private List<@NotBlank(message = "Project tags cannot be blank.") String> tags;
    private Map<String, String> links;

    @Pattern(
            regexp = "^$|https://(github\\.com|gitlab\\.com|codeberg\\.org)/[\\w.-]+/[\\w.-]+$",
            message = "Repository links must be valid HTTPS GitHub, GitLab, or Codeberg URLs."
    )
    private String repositoryUrl;
    private String license;
    private Boolean customLicenseOpenSource;
    private Boolean allowModpacks;
    private Boolean allowComments;
    private Boolean adsEnabled;
    private Boolean donationsEnabled;
    private Integer suggestedDonationCents;
    private Boolean donationRecurringDefault;
    private Boolean hmWikiEnabled;
    private Boolean galleryCarouselEnabled;

    @Pattern(
            regexp = "^$|^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$",
            message = "Wiki slugs must be 3-50 lowercase characters and may include dashes."
    )
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
    public Boolean getCustomLicenseOpenSource() { return customLicenseOpenSource; }
    public void setCustomLicenseOpenSource(Boolean customLicenseOpenSource) { this.customLicenseOpenSource = customLicenseOpenSource; }
    public Boolean getAllowModpacks() { return allowModpacks; }
    public void setAllowModpacks(Boolean allowModpacks) { this.allowModpacks = allowModpacks; }
    public Boolean getAllowComments() { return allowComments; }
    public void setAllowComments(Boolean allowComments) { this.allowComments = allowComments; }
    public Boolean getAdsEnabled() { return adsEnabled; }
    public void setAdsEnabled(Boolean adsEnabled) { this.adsEnabled = adsEnabled; }
    public Boolean getDonationsEnabled() { return donationsEnabled; }
    public void setDonationsEnabled(Boolean donationsEnabled) { this.donationsEnabled = donationsEnabled; }
    public Integer getSuggestedDonationCents() { return suggestedDonationCents; }
    public void setSuggestedDonationCents(Integer suggestedDonationCents) { this.suggestedDonationCents = suggestedDonationCents; }
    public Boolean getDonationRecurringDefault() { return donationRecurringDefault; }
    public void setDonationRecurringDefault(Boolean donationRecurringDefault) { this.donationRecurringDefault = donationRecurringDefault; }
    public Boolean getHmWikiEnabled() { return hmWikiEnabled; }
    public void setHmWikiEnabled(Boolean hmWikiEnabled) { this.hmWikiEnabled = hmWikiEnabled; }
    public Boolean getGalleryCarouselEnabled() { return galleryCarouselEnabled; }
    public void setGalleryCarouselEnabled(Boolean galleryCarouselEnabled) { this.galleryCarouselEnabled = galleryCarouselEnabled; }
    public String getHmWikiSlug() { return hmWikiSlug; }
    public void setHmWikiSlug(String hmWikiSlug) { this.hmWikiSlug = hmWikiSlug; }
}
