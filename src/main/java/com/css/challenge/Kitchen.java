package com.css.challenge;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Business.Location;
import com.css.challenge.Business.Temperature;
import com.css.challenge.Storage.CoolerStorage;
import com.css.challenge.Storage.HeaterStorage;
import com.css.challenge.Storage.ShelfStorage;
import com.css.challenge.Storage.StorageRepository;
import com.css.challenge.Strategies.DiscardStrategy;
import com.css.challenge.Strategies.FreshnessDiscardStrategy;
import com.css.challenge.client.Action;
import com.css.challenge.client.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
    Handles all orders and their location for food and where they are tracked
 */
public class Kitchen {

    private static final Logger LOGGER = LoggerFactory.getLogger(Kitchen.class);

    // different storage options
    private StorageRepository cooler = new CoolerStorage();
    private StorageRepository heater = new HeaterStorage();
    private StorageRepository shelf = new ShelfStorage();

    // actions taken
    private List<Action> actions = new ArrayList<>();

    // metrics
    private int totalOrdersPlaced = 0;
    private int totalOrdersPickedUp = 0;
    private int totalDiscaredExpiredOrders = 0;
    private int totalDiscaredOverflowOrder = 0;

    // Choose how we discard item/orders
    DiscardStrategy discardStrategy;

    // used for dealing with threads and locking critical areas
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public Kitchen(){
        this(new FreshnessDiscardStrategy());
    }

    public Kitchen(DiscardStrategy discardStrategy){
        this.discardStrategy = discardStrategy;
        LOGGER.info("Kitchen will use {} discard strategy for order", discardStrategy.getName());
    }

    public void placeOrder(KitchenOrder order){
        lock.writeLock().lock();
        try {
            LOGGER.info("Placing order {} temp: {}", order.getId(), order.getTemperature());

            // If it is a hot or cold order, check their storage areas
            if (canPlaceInIdealStorage(order)) {
                recordActionTaken(Instant.now(), order, Action.PLACE, getLocation(order));
                totalOrdersPlaced++;
                return;
            }

            //Otherwise check the shelf for orders
            if (shelf.hasSpace()) {
                shelf.add(order);
                recordActionTaken(Instant.now(), order, Action.PLACE, Location.SHELF.getValue());
                totalOrdersPlaced++;
                LOGGER.info("Placed {} on shelf (no space in ideal storage)", order.getId());
                return;
            }

            //If we can move from the shelf to hot/cold storage based on changes in the kitchen storage
            if (canRelocateFromShelf()) {
                shelf.add(order);
                recordActionTaken(Instant.now(), order, Action.PLACE, Location.SHELF.getValue());
                LOGGER.info("Placed {} on shelf (after relocating)", order.getId());
                totalOrdersPlaced++;
                return;
            }

            // check for discarding any items based on the strategy selected (ex: freshness ratio)
            Optional<KitchenOrder> discardedOrder = discardStrategy.selectDiscardCandidate(shelf);

            if (discardedOrder.isPresent()) {
                shelf.remove(discardedOrder.get().getId());
                recordActionTaken(Instant.now(), discardedOrder.get(), Action.DISCARD, discardedOrder.get().getCurrentLocation().getValue(), "Overflow and discarded for new order");
                totalDiscaredOverflowOrder++;
                LOGGER.warn("Discarded {} to make room for {}", discardedOrder.get().getId(), order.getId());
            }

            shelf.add(order);
            recordActionTaken(Instant.now(), order, Action.PLACE, Location.SHELF.getValue());
            totalOrdersPlaced++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<KitchenOrder> pickupOrder(String orderId){
        lock.writeLock().lock();
        try {
            Optional<KitchenOrder> order = findOrderInStorage(orderId);

            if (order.isEmpty()) {
                LOGGER.warn("Pickup attempted: order {} not found", orderId);
                return Optional.empty();
            }

            KitchenOrder pickedUpOrder = order.get();
            Instant now = Instant.now();

            if (pickedUpOrder.hasExpired(now)) {
                Location currentLocation = pickedUpOrder.getCurrentLocation();
                removeFromStorage(pickedUpOrder);
                recordActionTaken(now, pickedUpOrder, Action.PICKUP, currentLocation.getValue(), String.format("Expired (%.1f seconds remaining)", pickedUpOrder.getRemainingFreshness(now)));
                totalDiscaredExpiredOrders++;
                LOGGER.warn("Pickup failed: order {} expired", orderId);
                return Optional.empty();
            }

            Location location = pickedUpOrder.getCurrentLocation();
            removeFromStorage(pickedUpOrder);
            recordActionTaken(now, pickedUpOrder, Action.PICKUP, location.getValue());
            totalOrdersPickedUp++;
            LOGGER.info("Picked up Order {} from {}", orderId, location);
            return Optional.of(pickedUpOrder);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Action> getActions(){
        lock.readLock().lock();
        try {
            return new ArrayList<>(actions);
        } finally {
            lock.readLock().lock();
        }
    }

    /**
     * Place in ideal storage location within lock
     *
     * @param order being placed
     * @return true if successfully placed item
     */
    private boolean canPlaceInIdealStorage(KitchenOrder order){
        StorageRepository ideal = getIdealStorage(order);

        if(ideal.hasSpace()){
            ideal.add(order);
            LOGGER.debug("Placed {} in ideal storage", order.getId());
            return true;
        }

        return false;

    }

    /**
     * Relocate to existing shelf within lock
     *
     * @return true if relocated, false if no space
     */
    private boolean canRelocateFromShelf(){
        if(cooler.hasSpace()){
            //get cool orders to move to cooler
            Optional<KitchenOrder> coldItemToMove =
                    shelf.getAllOrders().stream().
                            filter(o -> o.getTemperature() == Temperature.COLD)
                            .max(Comparator.comparing(KitchenOrder::getPrice));

            if(coldItemToMove.isPresent()){
                KitchenOrder order = coldItemToMove.get();
                shelf.remove(order.getId());
                cooler.add(order);

                recordActionTaken(Instant.now(), order, Action.MOVE, Location.COOLER.getValue(), "Relocated from shelf to ideal location" );
                LOGGER.info("Relocated {} from shelf to cooler", order.getId());
                return true;
            }
        }

        if(heater.hasSpace()){
            Optional<KitchenOrder> hotItemToMove =
                    shelf.getAllOrders().stream().
                            filter(o -> o.getTemperature() == Temperature.HOT)
                            .max(Comparator.comparing(KitchenOrder::getPrice));

            if(hotItemToMove.isPresent()){
                KitchenOrder order = hotItemToMove.get();
                shelf.remove(order.getId());
                heater.add(order);
                recordActionTaken(Instant.now(), order, Action.MOVE, Location.HEATER.getValue(), "Relocated from shelf to ideal location" );
                LOGGER.info("Relocated {} from shelf to heater", order.getId());
                return true;
            }
        }

        return false;
    }

    private Optional<KitchenOrder> selectDiscardCandidate(){
        Instant now = Instant.now();
        List<KitchenOrder> allOrders = shelf.getAllOrders();

        // compare which is the freshest one from all the kitchen order
        return allOrders.stream().min((o1, o2) ->{
            double ratio1 = o1.getFreshnessRatio(now);
            double ratio2 = o2.getFreshnessRatio(now);

            int freshnessRatioCompare = Double.compare(ratio1, ratio2);
            if(freshnessRatioCompare != 0){
                return freshnessRatioCompare;
            }

            // if the same freshness ratio (both going to expire at the same rate) so compare price
            return o1.getPrice().compareTo(o2.getPrice());
        });
    }

    private StorageRepository getIdealStorage(KitchenOrder order){
        return switch (order.getTemperature()){
            case HOT -> heater;
            case COLD -> cooler;
            case ROOM -> shelf;
        };
    }

    private String getLocation(KitchenOrder order){
        Location currentLocation = order.getCurrentLocation();
        return currentLocation == null ? "unknown" : currentLocation.toString(); //CHECK THIS VALUE
    }

    private Optional<KitchenOrder> findOrderInStorage(String orderId){
        Optional<KitchenOrder> foundOrder = cooler.findById(orderId);
        if(foundOrder.isPresent()){
            return foundOrder;
        }

        foundOrder = heater.findById(orderId);
        if(foundOrder.isPresent()){
            return foundOrder;
        }

        return shelf.findById(orderId);
    }

    private void removeFromStorage(KitchenOrder order){
        String orderId = order.getId();
        if(!cooler.remove(orderId)){
            if(!heater.remove(orderId)){
                shelf.remove(order.getId());
            }
        }
    }

    private void recordActionTaken(Instant timestamp, KitchenOrder order, String action, String target){
        recordActionTaken(timestamp, order, action, target, null);
    }

    private void recordActionTaken(Instant timestamp, KitchenOrder order, String action, String target, String reason){
        Action record = new Action(timestamp, order.getId(), action, target);
        actions.add(record);

        if(reason != null){
            LOGGER.info("[{}] {} {} {} ({})", timestamp, action.toUpperCase(), order.getId(), target, reason);
        } else{
            LOGGER.info("[{}] {} {} {}", timestamp, action.toUpperCase(), order.getId(), target);
        }
    }
}
