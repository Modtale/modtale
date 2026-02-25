package net.modtale.model.jam;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "modjams")
public class Modjam {

    @Id
    private String id;
    private String slug;
    private String title;
    private String description;
    private String rules;

    private String imageUrl;
    private String bannerUrl;

    private String hostId;
    private String hostName;

    private Instant startDate;
    private Instant endDate;
    private Instant votingEndDate;

    private String status = "DRAFT";

    private List<String> participantIds = new ArrayList<>();

    public static class Category {
        private String id;
        private String name;
        private String description;
        private int maxScore;

        public Category() {}

        public Category(String id, String name, String description, int maxScore) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.maxScore = maxScore;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getMaxScore() { return maxScore; }
        public void setMaxScore(int maxScore) { this.maxScore = maxScore; }
    }

    public static class Restrictions {
        private boolean requireNewProject;
        private boolean requireSourceRepo;
        private boolean requireOsiLicense;
        private Integer minContributors;
        private Integer maxContributors;
        private boolean requireUniqueSubmission;
        private boolean requireNewbie;
        private List<String> allowedLicenses = new ArrayList<>();
        private List<String> allowedClassifications = new ArrayList<>();
        private List<String> allowedGameVersions = new ArrayList<>();
        private String requiredDependencyId;
        private String requiredClassUsage;

        public boolean isRequireNewProject() { return requireNewProject; }
        public void setRequireNewProject(boolean requireNewProject) { this.requireNewProject = requireNewProject; }
        public boolean isRequireSourceRepo() { return requireSourceRepo; }
        public void setRequireSourceRepo(boolean requireSourceRepo) { this.requireSourceRepo = requireSourceRepo; }
        public boolean isRequireOsiLicense() { return requireOsiLicense; }
        public void setRequireOsiLicense(boolean requireOsiLicense) { this.requireOsiLicense = requireOsiLicense; }
        public Integer getMinContributors() { return minContributors; }
        public void setMinContributors(Integer minContributors) { this.minContributors = minContributors; }
        public Integer getMaxContributors() { return maxContributors; }
        public void setMaxContributors(Integer maxContributors) { this.maxContributors = maxContributors; }
        public boolean isRequireUniqueSubmission() { return requireUniqueSubmission; }
        public void setRequireUniqueSubmission(boolean requireUniqueSubmission) { this.requireUniqueSubmission = requireUniqueSubmission; }
        public boolean isRequireNewbie() { return requireNewbie; }
        public void setRequireNewbie(boolean requireNewbie) { this.requireNewbie = requireNewbie; }
        public List<String> getAllowedLicenses() { return allowedLicenses; }
        public void setAllowedLicenses(List<String> allowedLicenses) { this.allowedLicenses = allowedLicenses; }
        public List<String> getAllowedClassifications() { return allowedClassifications; }
        public void setAllowedClassifications(List<String> allowedClassifications) { this.allowedClassifications = allowedClassifications; }
        public List<String> getAllowedGameVersions() { return allowedGameVersions; }
        public void setAllowedGameVersions(List<String> allowedGameVersions) { this.allowedGameVersions = allowedGameVersions; }
        public String getRequiredDependencyId() { return requiredDependencyId; }
        public void setRequiredDependencyId(String requiredDependencyId) { this.requiredDependencyId = requiredDependencyId; }
        public String getRequiredClassUsage() { return requiredClassUsage; }
        public void setRequiredClassUsage(String requiredClassUsage) { this.requiredClassUsage = requiredClassUsage; }
    }

    private List<Category> categories = new ArrayList<>();
    private Restrictions restrictions = new Restrictions();

    private boolean allowPublicVoting;
    private boolean allowConcurrentVoting;
    private boolean showResultsBeforeVotingEnds;
    private boolean oneEntryPerPerson = true;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public Instant getVotingEndDate() { return votingEndDate; }
    public void setVotingEndDate(Instant votingEndDate) { this.votingEndDate = votingEndDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getParticipantIds() { return participantIds; }
    public void setParticipantIds(List<String> participantIds) { this.participantIds = participantIds; }
    public List<Category> getCategories() { return categories; }
    public void setCategories(List<Category> categories) { this.categories = categories; }
    public Restrictions getRestrictions() { return restrictions; }
    public void setRestrictions(Restrictions restrictions) { this.restrictions = restrictions; }
    public boolean isAllowPublicVoting() { return allowPublicVoting; }
    public void setAllowPublicVoting(boolean allowPublicVoting) { this.allowPublicVoting = allowPublicVoting; }
    public boolean isAllowConcurrentVoting() { return allowConcurrentVoting; }
    public void setAllowConcurrentVoting(boolean allowConcurrentVoting) { this.allowConcurrentVoting = allowConcurrentVoting; }
    public boolean isShowResultsBeforeVotingEnds() { return showResultsBeforeVotingEnds; }
    public void setShowResultsBeforeVotingEnds(boolean showResultsBeforeVotingEnds) { this.showResultsBeforeVotingEnds = showResultsBeforeVotingEnds; }
    public boolean isOneEntryPerPerson() { return oneEntryPerPerson; }
    public void setOneEntryPerPerson(boolean oneEntryPerPerson) { this.oneEntryPerPerson = oneEntryPerPerson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}