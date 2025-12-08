package com.css.challenge.Adapter;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Business.Temperature;
import com.css.challenge.client.Order;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 *  OrderAdapter is responsible for converting external client-facing
 *  objects (received from the simulation harness or remote client)
 *  into internal KitchenOrder domain models used by the kitchen logic.
 */
public class OrderAdapter {

    /**
     * Convert a client order into a domain KitchenOrder using the simulation time as the creation timestamp.
     *
     * @param clientOrder the client order
     * @return KitchenOrder domain object
     */
    public static KitchenOrder toDomain(Order clientOrder, Instant createdAt) {
        return KitchenOrder.builder()
                .id(clientOrder.getId())
                .name(clientOrder.getName())
                .temperature(parseTempSafe(clientOrder.getTemp()))
                .price(BigDecimal.valueOf(clientOrder.getPrice()))
                .freshness(Duration.ofSeconds(clientOrder.getFreshness()))
                .build();
    }

    /**
     * Safely parses temperature strings (hot/cold/room).
     *
     * @param raw the raw temperature string from the client order
     * @return the parsed Temperature, or ROOM if unknown
     */
    private static Temperature parseTempSafe(String raw) {
        if (raw == null) return Temperature.ROOM; // safest fallback

        String v = raw.trim().toLowerCase();

        return switch (v) {
            case "hot"  -> Temperature.HOT;
            case "cold" -> Temperature.COLD;
            case "room" -> Temperature.ROOM;
            default -> {
                yield Temperature.ROOM;
            }
        };
    }

}
