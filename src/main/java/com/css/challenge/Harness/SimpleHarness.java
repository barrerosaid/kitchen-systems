package com.css.challenge.Harness;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Kitchen;
import com.css.challenge.client.Action;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public SimpleHarnessResult run(List<KitchenOrder> orders){
        LOGGER.info("Starting simulation with {} orders", orders.size());
        long startTime = System.currentTimeMillis();

        List<String> orderIds = new ArrayList<>();
        // must be invoked once
        CountDownLatch orderPlacementComplete = new CountDownLatch(1);
        CountDownLatch orderPickupsComplete = new CountDownLatch(orders.size());

        // There are 1 Producer and 4 Consumers based on order list size
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1 + Math.min(4, orders.size()));

        try{
            //Producers
            executorService.submit(() -> {
                try{
                    for(KitchenOrder order: orders){
                        kitchen.placeOrder(order);
                        orderIds.add(order.getId());

                        // wait before next order based on placement rate
                        if(orders.indexOf(order) < orders.size()-1){
                            Thread.sleep(placementRate.toMillis());
                        }
                    }
                    LOGGER.info("Added all orders from kitchen");
                } catch(InterruptedException ex) {
                    LOGGER.error("Placement interrupted exception", ex);
                    Thread.currentThread().interrupt();
                } finally {
                    orderPlacementComplete.countDown();
                }
            });

            for(String orderId: orderIds){
                long pickupDaysMillis = findRandomPickupDelay();

                executorService.schedule(
                        () -> {
                            try{
                                var orderToPickUp = kitchen.pickupOrder(orderId);
                                if(orderToPickUp.isPresent()){
                                    LOGGER.debug("Picked up order {}", orderId);
                                } else{
                                    LOGGER.debug("Unable to pick up order {}", orderId);
                                }
                            } finally {
                                orderPickupsComplete.countDown();
                            }
                        },
                        pickupDaysMillis,
                        TimeUnit.MILLISECONDS
                );
            }
            long endTime = System.currentTimeMillis();
            List<Action> actions = kitchen.getActions();
            return new SimpleHarnessResult(kitchen, actions, startTime, endTime);
        } finally{
            executorService.shutdown();
            try{
                if(!executorService.awaitTermination(5, TimeUnit.SECONDS)){
                    executorService.shutdownNow();
                }
            } catch(InterruptedException ex){
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
