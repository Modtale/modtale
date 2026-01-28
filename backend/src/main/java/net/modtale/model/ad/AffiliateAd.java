package net.modtale.model.ad;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "affiliate_ads")
public class AffiliateAd {
    @Id
    private String id;
    private String title;
    private String linkUrl;
    private boolean active;

    private String trackingParam;

    private List<AdCreative> creatives = new ArrayList<>();

    private int views;
    private int clicks;

    public AffiliateAd() {}

    public AffiliateAd(String title, String linkUrl) {
        this.title = title;
        this.linkUrl = linkUrl;
        this.active = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getTrackingParam() { return trackingParam; }
    public void setTrackingParam(String trackingParam) { this.trackingParam = trackingParam; }

    public List<AdCreative> getCreatives() { return creatives; }
    public void setCreatives(List<AdCreative> creatives) { this.creatives = creatives; }
    public int getViews() { return views; }
    public void setViews(int views) { this.views = views; }
    public int getClicks() { return clicks; }
    public void setClicks(int clicks) { this.clicks = clicks; }

    public String getFirstImage() {
        return creatives.isEmpty() ? null : creatives.get(0).getImageUrl();
    }
}