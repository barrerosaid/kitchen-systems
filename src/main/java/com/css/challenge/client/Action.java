package com.css.challenge.client;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Action is a json-friendly representation of an action. */
public class Action {
  public static final String PLACE = "place";
  public static final String MOVE = "move";
  public static final String PICKUP = "pickup";
  public static final String DISCARD = "discard";

  public static final String HEATER = "heater";
  public static final String COOLER = "cooler";
  public static final String SHELF = "shelf";

  private final long timestamp; // unix timestamp in microseconds
  private final String id; // order id
  private final String action; // place, move, pickup or discard
  private final String target; // heater, cooler or shelf. Target is the destination for move

  public Action(Instant timestamp, String id, String action, String target) {
    this.timestamp = ChronoUnit.MICROS.between(Instant.EPOCH, timestamp);
    this.id = id;
    this.action = action;
    this.target = target;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public String getId() {
    return id;
  }

  public String getAction() {
    return action;
  }

  public String getTarget() {
    return target;
  }

  @Override
  public String toString() {
    return "{timestamp: "
        + timestamp
        + ", id: "
        + id
        + ", action: "
        + action
        + ", target: "
        + target
        + "}";
  }
}
