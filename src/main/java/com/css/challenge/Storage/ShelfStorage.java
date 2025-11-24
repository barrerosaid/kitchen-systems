package com.css.challenge.Storage;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Business.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/*
    Holds room temperature food
 */
public class ShelfStorage implements StorageRepository {
    private static final int CAPACITY = 12;
    private static final String NAME = "Shelf";
    private static final Logger LOGGER = LoggerFactory.getLogger(ShelfStorage.class);

    private final List<KitchenOrder> orders = Collections.synchronizedList(new ArrayList<>());

    private PriorityQueue<OrderItem> ordersByFreshness;

    private Map<String, OrderItem> orderMap = Collections.synchronizedMap(new HashMap<>());

    /**
     * Used as Object for mapping the freshness to a KitchenOrder
     */
    private static class OrderItem{
        KitchenOrder order;
        double cachedFreshness;
        long lastOrderUpdateTime;

        OrderItem(KitchenOrder order){
            this.order = order;
            this.cachedFreshness = 1.0;
            this.lastOrderUpdateTime = System.currentTimeMillis();
        }

        double getCurrentFreshnessRatio(){
            long now = System.currentTimeMillis();
            long age = now - lastOrderUpdateTime;

            // older than 100ms which is the cache time so we will need to re-upade
            if(age > 100){
                cachedFreshness = order.getFreshnessRatio(Instant.now());
                lastOrderUpdateTime = now;
            }

            return cachedFreshness;
        }

        @Override
        public String toString(){
            return String.format("OrderItem{id=%s, freshness=%.2f", order.getId(), getCurrentFreshnessRatio());
        }
    }

    public ShelfStorage(){
        ordersByFreshness = new PriorityQueue<>((a, b) -> Double.compare(a.getCurrentFreshnessRatio(), b.getCurrentFreshnessRatio()));
    }

    @Override
    public boolean hasSpace(){
        return orders.size() < CAPACITY;
    }

    @Override
    public void add(KitchenOrder order){
        if(!hasSpace()){
            throw new IllegalStateException(
                    String.format("%s is full as capacity is: %d", NAME, CAPACITY));
        }

        orders.add(order);
        order.setCurrentLocation(Location.HEATER);
    }

    @Override
    public boolean remove(String orderId){
        return orders.removeIf(order -> order.getId().equals(orderId));
    }

    @Override
    public Optional<KitchenOrder> findById(String orderId){
        return orders.stream().filter(order -> order.getId().equals(orderId)).findFirst();
    }

    @Override
    public List<KitchenOrder> getAllOrders(){
        return Collections.unmodifiableList(new ArrayList<>(orders));
    }

    @Override
    public int getCurrentCount(){
        return orders.size();
    }

    @Override
    public int getCapacity(){
        return CAPACITY;
    }

    @Override
    public String getName(){
        return NAME;
    }

    public Optional<KitchenOrder> getOldestOrder(){
        return orders.isEmpty() ? Optional.empty() : Optional.of(orders.getFirst());
    }

    @Override
    public String toString(){
        return String.format("%s (%d/%d)", NAME, getCurrentCount(), CAPACITY);
    }
}
