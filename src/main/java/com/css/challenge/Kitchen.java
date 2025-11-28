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

            LOGGER.info("Kitchen Status: Cooler={}/{} Heater={}/{} Shelf={}/{}",
                    coolerStorage.getCurrentCount(), coolerStorage.getCapacity(),
                    heaterStorage.getCurrentCount(), heaterStorage.getCapacity(),
                    shelfStorage.getCurrentCount(), shelfStorage.getCapacity());

            StorageRepository ideal = getStorage(order.getTemperature());
            LOGGER.warn("PLACE_START id={} temp={} ideal={} time={}",
                    order.getId(),
                    order.getTemperature(),
                    ideal.getName(),
                    now);

            LOGGER.debug("Kitchen: Ideal storage for order {} is {}", order.getId(), ideal.getName());
            LOGGER.info("Kitchen Debug: Placing order {} temp={} ideal storage={} shelf={}/{}",
                    order.getId(),
                    order.getTemperature(),
                    ideal.getName(),
                    shelfStorage.getCurrentCount(),
                    shelfStorage.getCapacity());

            // 1) Try ideal storage
            if (ideal.hasSpace()) {
                ideal.add(order);
                recordAction(now, order, Action.PLACE, ideal.getLocationName());
                totalOrdersPlaced++;
                LOGGER.info("Kitchen: Order {} placed in ideal storage {}", order.getId(), ideal.getName());
                return;
            } else {
                LOGGER.debug("Kitchen: Ideal storage {} is full for order {}", ideal.getName(), order.getId());
            }

            // 2) Try shelf
            if (shelfStorage.hasSpace()) {
                shelfStorage.add(order);
                recordAction(now, order, Action.PLACE, shelfStorage.getLocationName());
                totalOrdersPlaced++;
                LOGGER.info("Kitchen: Order {} placed in shelf storage", order.getId());
                return;
            } else {
                LOGGER.debug("Kitchen: Shelf storage is full for order {}", order.getId());
            }

            // 3) Discard from ideal using strategy
            Optional<KitchenOrder> discardInIdeal = discardStrategy.selectDiscardCandidate(ideal, now);
            if (discardInIdeal.isPresent()) {
                LOGGER.debug("Kitchen: Discard strategy selected order {} in ideal storage", discardInIdeal.get().getId());
                discardOrder(discardInIdeal.get(), now);
                ideal.add(order);
                recordAction(now, order, Action.PLACE, ideal.getLocationName());
                totalOrdersPlaced++;
                LOGGER.info("Kitchen: Order {} placed in ideal storage after discarding", order.getId());
                return;
            }

            // -----------------------------
            // 3.5) Move eligible orders from shelf to ideal storage
            // -----------------------------
            List<KitchenOrder> shelfOrders = new ArrayList<>(shelfStorage.getAllOrders()); // copy to avoid concurrent modification
            for (KitchenOrder shelfOrder : shelfOrders) {
                StorageRepository targetStorage = getStorage(shelfOrder.getTemperature());
                if (targetStorage.hasSpace()) {
                    shelfStorage.remove(shelfOrder.getId());
                    targetStorage.add(shelfOrder);
                    recordAction(now, shelfOrder, Action.MOVE, targetStorage.getLocationName());
                    LOGGER.info("Moved order {} from shelf to {}", shelfOrder.getId(), targetStorage.getName());
                }
            }

            // 4) Discard from shelf if necessary
            if (!shelfStorage.hasSpace()) {
                Optional<KitchenOrder> discardShelf = discardStrategy.selectDiscardCandidate(shelfStorage, now);
                if (discardShelf.isPresent()) {
                    LOGGER.debug("Kitchen: Discard strategy selected order {} in shelf storage", discardShelf.get().getId());
                    discardOrder(discardShelf.get(), now);
                } else {
                    LOGGER.debug("Kitchen: No candidate to discard in shelf storage");
                }
            }

            // 5) Try to place on shelf after moves/discards
            if (shelfStorage.hasSpace()) {
                shelfStorage.add(order);
                recordAction(now, order, Action.PLACE, shelfStorage.getLocationName());
                totalOrdersPlaced++;
                LOGGER.info("Kitchen: Order {} placed in shelf storage after move/discard attempt", order.getId());
                return;
            }

            // 6) Nothing worked — drop on floor
            LOGGER.warn("Kitchen: NO SPACE for order {} — could not be placed", order.getId());

        } finally {
            lock.writeLock().unlock();
        }
    }



    // ------------------------------------------------------------
    // PICKUP ORDER
    // ------------------------------------------------------------

    public Optional<KitchenOrder> pickupOrder(String id, Instant now) {
        lock.writeLock().lock();
        try {

            if (discardedOrderIds.contains(id)) {
                LOGGER.debug("Pickup skipped: {} already discarded", id);
                return Optional.empty();
            }

            Optional<KitchenOrder> found = findOrder(id);
            if (found.isEmpty()) {
                LOGGER.warn("Pickup attempted for unknown id {}", id);
                return Optional.empty();
            }

            KitchenOrder order = found.get();

            // Expired?
            if (order.hasExpired(now)) {
                LOGGER.warn("Pickup failed: {} expired", id);
                discardOrder(order, now);
                return Optional.empty();
            }

            StorageRepository storage = getStorage(order.getCurrentLocation());
            storage.remove(order.getId());

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
        LOGGER.error("DISCARD id={} from {} at {}",
                order.getId(),
                order.getCurrentLocation(),
                now);

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
    // METRICS (OPTIONAL)
    // ------------------------------------------------------------

    public int getTotalOrdersPlaced() { return totalOrdersPlaced; }
    public int getTotalOrdersPickedUp() { return totalOrdersPickedUp; }
    public int getTotalOrdersDiscardedExpired() { return totalOrdersDiscardedExpired; }
}
