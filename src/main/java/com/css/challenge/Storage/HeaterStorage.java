package com.css.challenge.Storage;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Business.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class HeaterStorage implements StorageRepository {
    private static final int CAPACITY = 6;
    private static final String NAME = "heater";

    private final List<KitchenOrder> orders = Collections.synchronizedList(new ArrayList<>());

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

    @Override
    public String getLocationName() {
        return NAME; // Heater, Cooler, Shelf, etc.
    }

    @Override
    public String toString(){
        return String.format("%s (%d/%d)", NAME, getCurrentCount(), CAPACITY);
    }
}
