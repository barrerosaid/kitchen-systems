package com.css.challenge.Storage;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Business.Location;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/*
    This StorageRepository interface is used for all storage locations
    Will define storage capacity, remove and add orders, look up orders by id
 */
public interface StorageRepository {

    // check for space
    public boolean hasSpace();

    //Add order to storage
    public void add(KitchenOrder order, Instant now);

    //remove order by ID
    public boolean remove(String orderId);

    //find order by ID without removing it
    Optional<KitchenOrder> findById(String orderId);

    //get all orders in this storage
    List<KitchenOrder> getAllOrders();

    //get number of orders in storage
    int getCurrentCount();

    //get storage capacity
    int getCapacity();

    //name of storage location
    String getName();

    String getLocationName();

    Location getLocation();
}
