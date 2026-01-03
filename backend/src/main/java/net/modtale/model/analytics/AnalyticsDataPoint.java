package net.modtale.model.analytics;

public class AnalyticsDataPoint {
    private String date;
    private int count;
    private double value;

    public AnalyticsDataPoint() {}

    public AnalyticsDataPoint(String date, int count) {
        this.date = date;
        this.count = count;
        this.value = (double) count;
    }

    public AnalyticsDataPoint(String date, double value) {
        this.date = date;
        this.value = value;
        this.count = (int) value;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
}