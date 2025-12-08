package com.css.challenge.Business;

/**
 * Location enum for heater, cooler, and shelf
 */
public enum Location {
    HEATER("heater"),
    COOLER("cooler"),
    SHELF("shelf");

    private final String value;

    Location(String value) {
        this.value = value;
    }

    public static Location fromString(String value){
        return switch(value.toLowerCase()) {
            case "heater" -> HEATER;
            case "cooler" -> COOLER;
            case "shelf" -> SHELF;
            default ->  throw new IllegalArgumentException("Unknown location " + value);
        };
    }

    @Override
    public String toString() {
        return value;
    }
}