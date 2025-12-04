package com.css.challenge;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Business.Location;
import com.css.challenge.Business.Temperature;
import com.css.challenge.Strategies.DiscardStrategy;
import com.css.challenge.Storage.StorageRepository;
import com.css.challenge.client.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Kitchen {

    private static final Logger LOGGER = LoggerFactory.getLogger(Kitchen.class);

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final StorageRepository heaterStorage;
    private final StorageRepository coolerStorage;
    private final StorageRepository shelfStorage;
    private final DiscardStrategy discardStrategy;

    private final Set<String> discardedOrderIds = new HashSet<>();
    private final List<Action> actionLog = new ArrayList<>();

    private int totalOrdersPlaced = 0;
    private int totalOrdersPickedUp = 0;
    private int totalOrdersDiscardedExpired = 0;

    public Kitchen(StorageRepository heater,
                   StorageRepository cooler,
                   StorageRepository shelf,
                   DiscardStrategy discardStrategy) {

        this.heaterStorage = heater;
        this.coolerStorage = cooler;
        this.shelfStorage = shelf;
        this.discardStrategy = discardStrategy;
    }

    // ------------------------------------------------------------
    // STORAGE RESOLUTION
    // ------------------------------------------------------------

    private StorageRepository getStorage(Temperature temp) {
        return switch (temp) {
            case HOT -> heaterStorage;
            case COLD -> coolerStorage;
            case ROOM -> shelfStorage;
        };
    }

    private StorageRepository getStorage(Location loc) {
        return switch (loc) {
            case HEATER -> heaterStorage;
            case COOLER -> coolerStorage;
            case SHELF -> shelfStorage;
        };
    }

    // ------------------------------------------------------------
    // ACTION RECORDING
    // ------------------------------------------------------------

    private void recordAction(Instant ts, KitchenOrder order, String action, String target) {
        Action a = new Action(ts, order.getId(), action, target);
        actionLog.add(a);
        LOGGER.info("ACTION {}", a);
    }

    public List<Action> getActions() {
        return List.copyOf(actionLog);
    }

    // ------------------------------------------------------------
    // PLACE ORDER
    // ------------------------------------------------------------
    public void placeOrder(KitchenOrder order, Instant now) {
        lock.writeLock().lock();
        try {
            LOGGER.info("Kitchen: Placing order {} at {}", order.getId(), now);
            order.setCreatedAt(now);

            StorageRepository ideal = getStorage(order.getTemperature());

            // --------------------------------------------------
            // 1) Try ideal storage
            // --------------------------------------------------
            if (ideal.hasSpace()) {
                ideal.add(order, now);
                order.setCurrentLocation(ideal.getLocation());
                recordAction(now, order, Action.PLACE, ideal.getLocationName());
                totalOrdersPlaced++;
                return;
            }

            // --------------------------------------------------
            // 2) Try shelf if ideal is full
            // --------------------------------------------------
            if (shelfStorage.hasSpace()) {
                shelfStorage.add(order, now);
                order.setCurrentLocation(shelfStorage.getLocation());
                recordAction(now, order, Action.PLACE, shelfStorage.getLocationName());
                totalOrdersPlaced++;
                return;
            }

            // --------------------------------------------------
            // 3) Shelf full → attempt to move orders to ideal first
            // --------------------------------------------------
            if (!moveOrderFromShelfIfPossible(now)) {
                // Could not move anything → discard least fresh shelf order
                Optional<KitchenOrder> discardShelf = discardStrategy.selectDiscardCandidate(shelfStorage, now);
                discardShelf.ifPresent(o -> discardOrder(o, now));
            }

            // --------------------------------------------------
            // 4) Place on shelf after possible move/discard
            // --------------------------------------------------
            if (shelfStorage.hasSpace()) {
                shelfStorage.add(order, now);
                order.setCurrentLocation(shelfStorage.getLocation());
                recordAction(now, order, Action.PLACE, shelfStorage.getLocationName());
                totalOrdersPlaced++;
                return;
            }

            // --------------------------------------------------
            // 5) Nothing worked — drop on floor
            // --------------------------------------------------
            LOGGER.warn("Kitchen: NO SPACE for order {} — could not be placed", order.getId());

        } finally {
            lock.writeLock().unlock();
        }
    }

    // ------------------------------------------------------------
    // MOVE ORDER FROM SHELF IF POSSIBLE
    // ------------------------------------------------------------
    private boolean moveOrderFromShelfIfPossible(Instant now) {
        List<KitchenOrder> shelfOrders = new ArrayList<>(shelfStorage.getAllOrders());

        for (KitchenOrder o : shelfOrders) {
            if (o.getFreshnessRatio(now) <= 0) continue; // skip expired
            StorageRepository ideal = getStorage(o.getTemperature());
            // Only move to ideal storage if it has space
            if (ideal.hasSpace()) {
                shelfStorage.remove(o.getId());
                ideal.add(o, now);
                o.setCurrentLocation(ideal.getLocation());
                recordAction(now, o, Action.MOVE, ideal.getLocationName());
                return true; // moved one order to make room
            }
        }

        // Nothing could be moved
        return false;
    }

    // ------------------------------------------------------------
    // PICKUP ORDER
    // ------------------------------------------------------------
    public Optional<KitchenOrder> pickupOrder(String id, Instant now) {
        lock.writeLock().lock();
        try {
            // Already discarded?
            if (discardedOrderIds.contains(id)) {
                return Optional.empty();
            }

            Optional<KitchenOrder> found = findOrder(id);
            if (found.isEmpty()) {
                LOGGER.warn("Pickup attempted for unknown id {}", id);
                return Optional.empty();
            }

            KitchenOrder order = found.get();

            // Expired? Discard it first
            if (order.hasExpired(now)) {
                discardOrder(order, now);
                return Optional.empty();
            }

            // Remove from storage
            StorageRepository storage = getStorage(order.getCurrentLocation());
            boolean removed = storage.remove(order.getId());
            if (!removed) {
                LOGGER.warn("Pickup failed: order {} not found in expected storage {}", id, storage.getName());
                return Optional.empty();
            }

            // Record pickup action
            recordAction(now, order, Action.PICKUP, storage.getLocationName());
            totalOrdersPickedUp++;

            LOGGER.info("Picked up {}", id);
            return Optional.of(order);

        } finally {
            lock.writeLock().unlock();
        }
    }

    // ------------------------------------------------------------
    // DISCARD ORDER
    // ------------------------------------------------------------
    private void discardOrder(KitchenOrder order, Instant now) {
        StorageRepository storage = getStorage(order.getCurrentLocation());
        storage.remove(order.getId());
        discardedOrderIds.add(order.getId());

        recordAction(now, order, Action.DISCARD, storage.getLocationName());
        totalOrdersDiscardedExpired++;

        LOGGER.info("Discarded {} via strategy {}", order.getId(), discardStrategy.getName());
    }

    // ------------------------------------------------------------
    // UTIL HELPERS
    // ------------------------------------------------------------
    private Optional<KitchenOrder> findOrder(String id) {
        return heaterStorage.findById(id)
                .or(() -> coolerStorage.findById(id))
                .or(() -> shelfStorage.findById(id));
    }

    // ------------------------------------------------------------
    // METRICS
    // ------------------------------------------------------------
    public int getTotalOrdersPlaced() { return totalOrdersPlaced; }
    public int getTotalOrdersPickedUp() { return totalOrdersPickedUp; }
    public int getTotalOrdersDiscardedExpired() { return totalOrdersDiscardedExpired; }
}
