package com.css.challenge.Harness;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Kitchen;
import com.css.challenge.client.Action;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *  Using Producer and Consumers pattern to simulate a kitchen receiving orders
 *
 *  Will select orders via a rate and schedule pickups at random times; coordinate concurrent operations; collect results
 *
 *  One thread produces an order/kitchen item at a given rate and then mutiple threads will consume and pick it up randomly
 */
public class SimpleHarness {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleHarness.class);

    private final Kitchen kitchen;
    private final Duration placementRate;
    private final Duration pickupMinDelay;
    private final Duration pickupMaxDelay;
    private final Random random = new Random();

    public SimpleHarness(
            Kitchen kitchen,
            Duration placementRate,
            Duration pickupMinDelay,
            Duration pickupMaxDelay) {

        this.kitchen = kitchen;
        this.placementRate = placementRate;
        this.pickupMinDelay = pickupMinDelay;
        this.pickupMaxDelay = pickupMaxDelay;

        LOGGER.info("Simple Harness is read: rate{}ms, pickup={}--{}",
                placementRate.toMillis(),
                pickupMinDelay.toSeconds(),
                pickupMaxDelay);
    }

    /**
     *
     * @param orders that represents list of orders to process
     * @return HarnessResult
     */
    public SimpleHarnessResult run(List<KitchenOrder> orders) {
        LOGGER.info("Starting simulation with {} orders", orders.size());
        long startTime = System.currentTimeMillis();

        List<String> orderIds = new ArrayList<>();
        CountDownLatch orderPlacementComplete = new CountDownLatch(1);
        CountDownLatch orderPickupsComplete = new CountDownLatch(orders.size());

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(
                1 + Math.min(4, orders.size())
        );

        try {
            // Producer: Place orders immediately (don't wait for their creation times)
            executorService.submit(() -> {
                try {
                    for (KitchenOrder order : orders) {
                        kitchen.placeOrderWithTimestamp(order);
                        orderIds.add(order.getId());
                    }
                    LOGGER.info("Added all orders from kitchen");
                } catch (Exception ex) {
                    LOGGER.error("Placement interrupted exception", ex);
                    Thread.currentThread().interrupt();
                } finally {
                    orderPlacementComplete.countDown();
                }
            });

            // Wait for placement to complete FIRST
            try {
                long placementTimeout = 10000; // 10 seconds should be plenty
                boolean placementsOk = orderPlacementComplete.await(placementTimeout, TimeUnit.MILLISECONDS);

                if (!placementsOk) {
                    LOGGER.error("Placements took too long");
                }
            } catch (InterruptedException ex) {
                LOGGER.error("Harness interrupted during placement", ex);
                Thread.currentThread().interrupt();
            }

            // NOW schedule pickups (orderIds is populated)
            for (String orderId : orderIds) {
                // Skip orders that were discarded during placement
                if (kitchen.isOrderDiscarded(orderId)) {
                    orderPickupsComplete.countDown();
                    continue;
                }

                long pickupDelayMillis = findRandomPickupDelay();

                executorService.schedule(
                        () -> {
                            try {
                                var orderToPickUp = kitchen.pickupOrder(orderId);
                                if (orderToPickUp.isPresent()) {
                                    LOGGER.debug("Picked up order {}", orderId);
                                } else {
                                    LOGGER.debug("Unable to pick up order {}", orderId);
                                }
                            } finally {
                                orderPickupsComplete.countDown();
                            }
                        },
                        pickupDelayMillis,
                        TimeUnit.MILLISECONDS
                );
            }

            // Wait for all pickups to complete
            try {
                long pickupTimeout = pickupMaxDelay.toMillis() + 5000;
                boolean pickupsOk = orderPickupsComplete.await(pickupTimeout, TimeUnit.MILLISECONDS);

                if (!pickupsOk) {
                    LOGGER.error("Pickups took too long");
                }
            } catch (InterruptedException ex) {
                LOGGER.error("Harness interrupted during pickups", ex);
                Thread.currentThread().interrupt();
            }

            long endTime = System.currentTimeMillis();
            List<Action> actions = kitchen.getActions();

            // Sort actions by timestamp
            actions.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

            LOGGER.info("Completed in {}ms with {} actions", endTime - startTime, actions.size());

            return new SimpleHarnessResult(kitchen, actions, startTime, endTime);

        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private long findRandomPickupDelay(){
        long minMillis = pickupMinDelay.toMillis();
        long maxMillis = pickupMaxDelay.toMillis();
        long difference = maxMillis - minMillis;
        return minMillis + random.nextLong(difference+1);
    }
}
