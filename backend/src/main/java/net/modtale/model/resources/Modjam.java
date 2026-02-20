package net.modtale.model.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "modjams")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Modjam {

    @Id
    private String id;

    @Indexed(unique = true)
    private String slug;

    @Indexed
    private String title;

    private String description;
    private String bannerUrl;

    @Indexed
    private String hostId;

    private String hostName;

    @Indexed
    private LocalDateTime startDate;

    @Indexed
    private LocalDateTime endDate;

    private LocalDateTime votingEndDate;

    @Indexed
    private String status = "DRAFT";

    private List<String> participantIds = new ArrayList<>();

    private List<Category> categories = new ArrayList<>();

    private boolean allowPublicVoting = true;

    private LocalDateTime createdAt = LocalDateTime.now();

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

    public Modjam() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public LocalDateTime getVotingEndDate() { return votingEndDate; }
    public void setVotingEndDate(LocalDateTime votingEndDate) { this.votingEndDate = votingEndDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getParticipantIds() { return participantIds; }
    public void setParticipantIds(List<String> participantIds) { this.participantIds = participantIds; }
    public List<Category> getCategories() { return categories; }
    public void setCategories(List<Category> categories) { this.categories = categories; }
    public boolean isAllowPublicVoting() { return allowPublicVoting; }
    public void setAllowPublicVoting(boolean allowPublicVoting) { this.allowPublicVoting = allowPublicVoting; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}