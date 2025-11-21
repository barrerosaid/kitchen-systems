package com.css.challenge.Business;

public enum Temperature {
    HOT("hot"),
    COLD("cold"),
    ROOM("room");

    private final String value;

    Temperature(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
