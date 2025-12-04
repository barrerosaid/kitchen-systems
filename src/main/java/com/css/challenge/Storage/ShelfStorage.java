package com.css.challenge.Storage;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Business.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.Duration;
import java.util.*;

public class ShelfStorage implements StorageRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShelfStorage.class);

    private static final int CAPACITY = 12;
    private static final String NAME = "shelf";

    private final List<KitchenOrder> orders = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, OrderItem> orderMap = Collections.synchronizedMap(new HashMap<>());
    private final PriorityQueue<OrderItem> ordersByFreshnessQueue;

    private static class OrderItem {
        final KitchenOrder order;
        double cachedFreshness;
        Instant lastUpdateTime;

        OrderItem(KitchenOrder order, Instant now) {
            this.order = order;
            this.cachedFreshness = 1.0;
            this.lastUpdateTime = now;
        }

        double getCurrentFreshnessRatio(Instant now) {
            if (Duration.between(lastUpdateTime, now).toMillis() > 50) { // update if stale
                cachedFreshness = order.getFreshnessRatio(now);
                lastUpdateTime = now;
            }
            LOGGER.debug("ShelfStorage: Calculated freshness for order {} = {}", order.getId(), cachedFreshness);
            return cachedFreshness;
        }
    }

    public ShelfStorage() {
        ordersByFreshnessQueue = new PriorityQueue<>(Comparator.comparingDouble(item -> item.cachedFreshness));
    }

    @Override
    public boolean hasSpace() {
        return orderMap.size() < CAPACITY;
    }

    public void add(KitchenOrder order, Instant now) {
        LOGGER.warn("{} ADD id={} temp={} count={}/{}",
                getName(),
                order.getId(),
                order.getTemperature(),
                getCurrentCount(),
                getCapacity());

        if (!hasSpace()) {
            throw new IllegalStateException(NAME + " is full");
        }

        orders.add(order);
        OrderItem item = new OrderItem(order, now);  // <-- use now from Kitchen
        orderMap.put(order.getId(), item);
        ordersByFreshnessQueue.offer(item);
        order.setCurrentLocation(Location.SHELF);
    }

    @Override
    public boolean remove(String orderId) {
        LOGGER.warn("{} REMOVE id={} count={}/{}",
                getName(),
                orderId,
                getCurrentCount(),
                getCapacity());
        OrderItem item = orderMap.remove(orderId);
        if (item != null) {
            ordersByFreshnessQueue.remove(item);
            orders.remove(item.order);
            LOGGER.info("ShelfStorage Debug: Removed order {}. Current shelf count={}/{}", orderId, orders.size(), CAPACITY);
            LOGGER.debug("ShelfStorage: Removed order {}", orderId);
            return true;
        }
        return false;
    }

    @Override
    public Optional<KitchenOrder> findById(String orderId) {
        return Optional.ofNullable(orderMap.get(orderId)).map(item -> item.order);
    }

    public Optional<KitchenOrder> findLeastFreshOrder(Instant now) {
        if (ordersByFreshnessQueue.isEmpty()) return Optional.empty();

        OrderItem top = ordersByFreshnessQueue.peek();
        top.getCurrentFreshnessRatio(now);
        LOGGER.debug("ShelfStorage: Least fresh order is {}", top.order.getId());

        List<OrderItem> items = new ArrayList<>(ordersByFreshnessQueue);
        ordersByFreshnessQueue.clear();
        ordersByFreshnessQueue.addAll(items);

        return Optional.of(top.order);
    }

    @Override
    public List<KitchenOrder> getAllOrders() {
        // Return a copy to avoid external modification
        return Collections.unmodifiableList(new ArrayList<>(orders));
    }

    @Override
    public int getCurrentCount() {
        // Number of orders currently stored
        return orderMap.size();
    }

    @Override
    public int getCapacity() {
        // Max capacity constant
        return CAPACITY;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Location getLocation() {
        return Location.SHELF;
    }

    @Override
    public String getLocationName() {
        return NAME; // Shelf's location is its name
    }

}
