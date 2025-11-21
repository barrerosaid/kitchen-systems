package com.css.challenge.client;

import java.util.List;

/** Problem represents a test problem */
public class Problem {
  private final String testId;
  private final List<Order> orders;

  public Problem(String testId, List<Order> orders) {
    this.testId = testId;
    this.orders = orders;
  }

  public String getTestId() {
    return testId;
  }

  public List<Order> getOrders() {
    return orders;
  }
}
