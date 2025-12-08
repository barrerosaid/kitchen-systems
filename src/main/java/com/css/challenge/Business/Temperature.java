package com.css.challenge.Business;

/**
 * Enum for hot, cold and room temperature
 */
public enum Temperature {
    HOT("hot"),
    COLD("cold"),
    ROOM("room");

    private final String value;

    Temperature(String value) {
        this.value = value;
    }

    // Get String representation of Enum
    public String getValue() {
        return value;
    }

    //string to Enum
    public static Temperature fromString(String value){
        return switch(value.toLowerCase()) {
            case "hot" -> HOT;
            case "cold" -> COLD;
            case "room" -> ROOM;
            default ->  throw new IllegalArgumentException("Unknown temperature " + value);
        };
    }

    @Override
    public String toString() {
        return value;
    }
}
