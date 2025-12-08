package com.css.challenge.Business;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public class KitchenOrder {

    private final String id;
    private final String name;
    private final Temperature temperature;
    private final BigDecimal price;
    private final Duration freshnessDuration;
    private Instant createdAt;
    private Location currentLocation;

    private KitchenOrder(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.temperature = builder.temperature;
        this.price = builder.price;
        this.freshnessDuration = builder.freshnessDuration;
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private Temperature temperature;
        private BigDecimal price;
        private Duration freshnessDuration;
        private Instant createdAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder temperature(Temperature temp) { this.temperature = temp; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder freshness(Duration duration) { this.freshnessDuration = duration; return this; }

        public KitchenOrder build() {
            if (id == null || temperature == null || freshnessDuration == null) {
                throw new IllegalStateException("KitchenOrder missing required fields");
            }
            return new KitchenOrder(this);
        }
    }

    /**
     * Getters for KitchenOrder
     */
    public String getId() { return id; }
    public String getName() { return name; }
    public Temperature getTemperature() { return temperature; }
    public BigDecimal getPrice() { return price; }
    public Duration getFreshnessDuration() { return freshnessDuration; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCurrentLocation(Location loc) { this.currentLocation = loc; }
    public Location getCurrentLocation() { return currentLocation; }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * TTL & freshness
     */
    public double getFreshnessRatio(Instant now) {
        long age = java.time.Duration.between(createdAt, now).toMillis();
        double ratio = Math.max(0, 1.0 - ((double) age / freshnessDuration.toMillis()));
        return ratio;
    }

    /**
     * Check if the order has expired
     * @param now
     * @return boolean
     */
    public boolean hasExpired(Instant now) {
        return now.isAfter(createdAt.plus(freshnessDuration));
    }
}
