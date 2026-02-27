package net.modtale.model.jam;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Document(collection = "modjam_submissions")
public class ModjamSubmission {

    @Id
    private String id;
    private String jamId;
    private String projectId;

    private String projectTitle;
    private String projectImageUrl;
    private String projectBannerUrl;
    private String projectAuthor;
    private String projectDescription;

    private String submitterId;
    private List<Vote> votes = new ArrayList<>();

    private Map<String, Double> categoryScores;
    private Double totalScore;

    private Map<String, Double> judgeCategoryScores;
    private Double totalJudgeScore;
    private Double totalPublicScore;

    private Integer rank;

    private boolean isWinner;
    private String awardTitle;

    private Instant createdAt = Instant.now();

    @Transient
    private Integer votesCast;

    @Transient
    private Integer commentsGiven;

    public static class Vote {
        private String id;
        private String voterId;
        private String categoryId;
        private int score;
        private boolean isJudge;

        public Vote() {}

        public Vote(String id, String voterId, String categoryId, int score, boolean isJudge) {
            this.id = id;
            this.voterId = voterId;
            this.categoryId = categoryId;
            this.score = score;
            this.isJudge = isJudge;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getVoterId() { return voterId; }
        public void setVoterId(String voterId) { this.voterId = voterId; }
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public boolean isJudge() { return isJudge; }
        public void setJudge(boolean judge) { isJudge = judge; }
    }

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
    public String getProjectBannerUrl() { return projectBannerUrl; }
    public void setProjectBannerUrl(String projectBannerUrl) { this.projectBannerUrl = projectBannerUrl; }
    public String getProjectAuthor() { return projectAuthor; }
    public void setProjectAuthor(String projectAuthor) { this.projectAuthor = projectAuthor; }
    public String getProjectDescription() { return projectDescription; }
    public void setProjectDescription(String projectDescription) { this.projectDescription = projectDescription; }
    public String getSubmitterId() { return submitterId; }
    public void setSubmitterId(String submitterId) { this.submitterId = submitterId; }
    public List<Vote> getVotes() { return votes; }
    public void setVotes(List<Vote> votes) { this.votes = votes; }
    public Map<String, Double> getCategoryScores() { return categoryScores; }
    public void setCategoryScores(Map<String, Double> categoryScores) { this.categoryScores = categoryScores; }
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    public Map<String, Double> getJudgeCategoryScores() { return judgeCategoryScores; }
    public void setJudgeCategoryScores(Map<String, Double> judgeCategoryScores) { this.judgeCategoryScores = judgeCategoryScores; }
    public Double getTotalJudgeScore() { return totalJudgeScore; }
    public void setTotalJudgeScore(Double totalJudgeScore) { this.totalJudgeScore = totalJudgeScore; }
    public Double getTotalPublicScore() { return totalPublicScore; }
    public void setTotalPublicScore(Double totalPublicScore) { this.totalPublicScore = totalPublicScore; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public boolean isWinner() { return isWinner; }
    public void setWinner(boolean winner) { this.isWinner = winner; }
    public String getAwardTitle() { return awardTitle; }
    public void setAwardTitle(String awardTitle) { this.awardTitle = awardTitle; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Integer getVotesCast() { return votesCast; }
    public void setVotesCast(Integer votesCast) { this.votesCast = votesCast; }
    public Integer getCommentsGiven() { return commentsGiven; }
    public void setCommentsGiven(Integer commentsGiven) { this.commentsGiven = commentsGiven; }
}