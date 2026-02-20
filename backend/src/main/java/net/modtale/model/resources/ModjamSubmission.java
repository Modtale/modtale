package net.modtale.model.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "modjam_submissions")
@JsonInclude(JsonInclude.Include.NON_NULL)
@CompoundIndexes({
        @CompoundIndex(name = "jam_rank_idx", def = "{'jamId': 1, 'rank': 1}")
})
public class ModjamSubmission {

    @Id
    private String id;

    @Indexed
    private String jamId;

    @Indexed
    private String projectId;

    private String projectTitle;
    private String projectImageUrl;

    @Indexed
    private String submitterId;

    private List<Vote> votes = new ArrayList<>();

    private Map<String, Double> categoryScores = new HashMap<>();

    private Double totalScore;

    private Integer rank;

    private LocalDateTime createdAt = LocalDateTime.now();

    public static class Vote {
        private String id;
        private String voterId;
        private String categoryId;
        private int score;

        public Vote() {}
        public Vote(String id, String voterId, String categoryId, int score) {
            this.id = id;
            this.voterId = voterId;
            this.categoryId = categoryId;
            this.score = score;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getVoterId() { return voterId; }
        public void setVoterId(String voterId) { this.voterId = voterId; }
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
    }

    public ModjamSubmission() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getJamId() { return jamId; }
    public void setJamId(String jamId) { this.jamId = jamId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }
    public String getProjectImageUrl() { return projectImageUrl; }
    public void setProjectImageUrl(String projectImageUrl) { this.projectImageUrl = projectImageUrl; }
    public String getSubmitterId() { return submitterId; }
    public void setSubmitterId(String submitterId) { this.submitterId = submitterId; }
    public List<Vote> getVotes() { return votes; }
    public void setVotes(List<Vote> votes) { this.votes = votes; }
    public Map<String, Double> getCategoryScores() { return categoryScores; }
    public void setCategoryScores(Map<String, Double> categoryScores) { this.categoryScores = categoryScores; }
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}