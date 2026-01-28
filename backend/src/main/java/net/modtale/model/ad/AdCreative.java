package net.modtale.model.ad;

import java.util.UUID;

public class AdCreative {
    private String id;
    private String imageUrl;
    private int width;
    private int height;
    private CreativeType type;

    public enum CreativeType {
        BANNER,
        SIDEBAR,
        CARD
    }

    public AdCreative() {}

    public AdCreative(String imageUrl, int width, int height) {
        this.id = UUID.randomUUID().toString();
        this.imageUrl = imageUrl;
        this.width = width;
        this.height = height;
        this.type = calculateType(width, height);
    }

    public static CreativeType calculateType(int w, int h) {
        double ratio = (double) w / h;
        if (ratio >= 2.5) return CreativeType.BANNER;
        if (ratio <= 0.8) return CreativeType.SIDEBAR;
        return CreativeType.CARD;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public CreativeType getType() { return type; }
    public void setType(CreativeType type) { this.type = type; }
}