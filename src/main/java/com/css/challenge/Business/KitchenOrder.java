package com.css.challenge.Business;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class KitchenOrder {
    private final String id;
    private final String name;
    private final Temperature temperature;
    private final BigDecimal price;
    private final Duration freshnessDuration;
    private final Instant createdAt;
    private volatile Location currentLocation;

    private KitchenOrder(Builder builder){
        this.id = Objects.requireNonNull(builder.id, "cannot be null");
        this.name = Objects.requireNonNull(builder.name, "cannot be null");
        this.temperature = Objects.requireNonNull(builder.temperature, "cannot be null");
        this.price = Objects.requireNonNull(builder.price, "cannot be null");
        this.freshnessDuration = Objects.requireNonNull(builder.freshnessDuration, "cannot be null");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "cannot be null");
    }

    public Temperature getTemperature() {
        return temperature;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public Duration getFreshnessDuration() {
        return freshnessDuration;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    /*
    An order not stored at ideal temperate degrades twice as quickly
     */
    public double getDecayRate(){
        if(currentLocation== null){
            return 1.0;
        }

        boolean isAtIdealTemperature = (temperature == Temperature.HOT && currentLocation == Location.HEATER)
                || (temperature == Temperature.COLD && currentLocation == Location.COOLER)
                || (temperature == Temperature.ROOM && currentLocation == Location.SHELF);

        return isAtIdealTemperature ? 1.0 : 2.0;
    }

    public double getRemainingFreshness(Instant now){
        long foodAgeInSeconds = Duration.between(createdAt, now).getSeconds();
        double decayRate = getDecayRate();
        double remaining = freshnessDuration.getSeconds() - (foodAgeInSeconds * decayRate);
        return Math.max(0, remaining);
    }

    public double getFreshnessRatio(Instant now){
        if(freshnessDuration.getSeconds() == 0){
            return 0;
        }

        double remaining = getRemainingFreshness(now);
        double total = freshnessDuration.getSeconds();
        return Math.min(1.0, remaining/total);
    }

    public boolean hasExpired(Instant now){
        return getRemainingFreshness(now) <= 0;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder{
        private String id;
        private String name;
        private Temperature temperature;
        private BigDecimal price;
        private Duration freshnessDuration;
        private Instant createdAt;

        public Builder id(String id){
            this.id = id;
            return this;
        }

        public Builder name(String name){
            this.name = name;
            return this;
        }

        public Builder temperature(Temperature temperature){
            this.temperature = temperature;
            return this;
        }

        public Builder price(BigDecimal price){
            this.price = price;
            return this;
        }

        public Builder freshness(Duration freshnessDuration){
            this.freshnessDuration = freshnessDuration;
            return this;
        }

        public Builder createdAt(Instant createdAt){
            this.createdAt = createdAt;
            return this;
        }


        public KitchenOrder build(){
            return new KitchenOrder(this);
        }

        public String toString(){
            return "KitchenOrder{"
                    +id+
                    ","+name+
                    ","+temperature+
                    ","+price+
                    ","+freshnessDuration+
                    +'}';
        }

        @Override
        public int hashCode(){
            return Objects.hash(id);
        }

        @Override
        public boolean equals(Object o){
            if(this == o){
                return true;
            }
            if(o == null || getClass() != o.getClass()){
                return false;
            }
            KitchenOrder kitchenOrder = (KitchenOrder) o;
            return Objects.equals(id, kitchenOrder.id);
        }
    }
}
