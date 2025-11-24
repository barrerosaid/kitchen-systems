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

    private PriorityQueue<OrderItem> ordersByFreshnessQueue;

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
        ordersByFreshnessQueue = new PriorityQueue<>((a, b) -> Double.compare(a.getCurrentFreshnessRatio(), b.getCurrentFreshnessRatio()));
    }

    @Override
    public boolean hasSpace(){
        return orderMap.size() < CAPACITY;
    }

    @Override
    public void add(KitchenOrder order){
        if(!hasSpace()){
            throw new IllegalStateException(
                    String.format("%s is full as capacity is: %d", NAME, CAPACITY));
        }

        OrderItem item = new OrderItem(order);
        orderMap.put(order.getId(), item);
        ordersByFreshnessQueue.offer(item);
        //orders.add(order);
        order.setCurrentLocation(Location.SHELF);

        LOGGER.debug("Added {} to shelf (count on shelf: {} / {})", order.getId(), orderMap.size(), CAPACITY);
    }

    @Override
    public boolean remove(String orderId){
        OrderItem item = orderMap.remove(orderId);
        if(item != null){
            ordersByFreshnessQueue.remove(item);
            LOGGER.debug("Removed {} from shelf (count: {}/{})", orderId, orderMap.size(), CAPACITY);
            return true;
        }
        return false;
        //return orders.removeIf(order -> order.getId().equals(orderId));
    }

    @Override
    public Optional<KitchenOrder> findById(String orderId){
        OrderItem item = orderMap.get(orderId);
        if(item == null){
            return Optional.empty();
        }

        return Optional.of(item.order);

        //return orders.stream().filter(order -> order.getId().equals(orderId)).findFirst();
    }

    @Override
    public List<KitchenOrder> getAllOrders(){
        return Collections.unmodifiableList(
                orderMap.values().stream()
                        .map(e -> e.order)
                        .toList()
        );

        //return Collections.unmodifiableList(new ArrayList<>(orders));
    }

    @Override
    public int getCurrentCount(){
        return orderMap.size();
    }

    @Override
    public int getCapacity(){
        return CAPACITY;
    }

    @Override
    public String getName(){
        return NAME;
    }

    //Select item that is the least fresh in the queue
    public Optional<KitchenOrder> findLeastFreshOrder(){
        OrderItem worstItem = ordersByFreshnessQueue.peek();

        if(worstItem == null){
            return Optional.empty();
        }

        return Optional.of(worstItem.order);
    }

    // FIFO
    public Optional<KitchenOrder> getOldestOrder(){
        return orders.isEmpty() ? Optional.empty() : Optional.of(orders.getFirst());
    }

    @Override
    public String toString(){
        return String.format("%s (%d/%d)", NAME, getCurrentCount(), CAPACITY);
    }
}
