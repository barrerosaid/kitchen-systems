package com.css.challenge.Harness;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Kitchen;
import com.css.challenge.client.Action;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

public class SimpleHarness {

    private final Kitchen kitchen;
    private final Duration placementRate;
    private final Duration pickupMin;
    private final Duration pickupMax;

    public SimpleHarness(Kitchen kitchen, Duration placementRate, Duration pickupMin, Duration pickupMax) {
        this.kitchen = kitchen;
        this.placementRate = placementRate;
        this.pickupMin = pickupMin;
        this.pickupMax = pickupMax;
    }

    public SimpleHarnessResult run(List<KitchenOrder> orders) {
        long startTime = System.currentTimeMillis();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
        List<ScheduledFuture<?>> scheduledPickups = new ArrayList<>();

        // Cumulative placement time to adjust pickup limits
        long cumulativePlacementMs = 0;

        for (KitchenOrder order : orders) {

            Instant placeTime = Instant.now();
            kitchen.placeOrder(order, placeTime);

            // Calculate maximum safe pickup delay
            Duration ttl = order.getFreshnessDuration();
            long maxPickupMs = Math.max(1, ttl.toMillis() - cumulativePlacementMs - 50); // 50ms buffer
            long minPickupMs = pickupMin.toMillis();
            long actualMaxMs = Math.min(maxPickupMs, pickupMax.toMillis());

            if (actualMaxMs < minPickupMs) {
                // Order would expire too fast; pick up immediately after placement
                actualMaxMs = minPickupMs;
            }

            long delayMs = ThreadLocalRandom.current().nextLong(minPickupMs, actualMaxMs + 1);

            ScheduledFuture<?> f = scheduler.schedule(() -> {
                Instant pickupTime = Instant.now();
                kitchen.pickupOrder(order.getId(), pickupTime);
            }, delayMs, TimeUnit.MILLISECONDS);

            scheduledPickups.add(f);

            // Wait only for placement rate, not pickup
            sleep(placementRate);
            cumulativePlacementMs += placementRate.toMillis();
        }

        // Wait for all pickups
        for (ScheduledFuture<?> f : scheduledPickups) {
            try {
                f.get();
            } catch (Exception ignored) {}
        }

        scheduler.shutdown();

        long endTime = System.currentTimeMillis();
        List<Action> actions = kitchen.getActions();

        return new SimpleHarnessResult(kitchen, actions, startTime, endTime);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Duration randomDuration(Duration min, Duration max, KitchenOrder order) {
        long freshnessMs = order.getFreshnessDuration().toMillis();
        long minMs = min.toMillis();
        long maxMs = Math.min(max.toMillis(), freshnessMs - 100); // leave a small buffer
        long random = ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
        return Duration.ofMillis(random);
    }
}
