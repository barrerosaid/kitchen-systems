package com.css.challenge.Business;

public enum Location {
    HEATER("heater"),
    COOLER("cooler"),
    SHELF("shelf");

    private final String value;

    Location(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}