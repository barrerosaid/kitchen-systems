package com.css.challenge.Adapter;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Business.Temperature;
import com.css.challenge.client.Order;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/*
    Adapter class to convert Order from Challenge Server to Kitchen Order object using Kitchen Order builder
 */
public class OrderAdapter {

    public static KitchenOrder toDomain(Order clientOrder){
        return KitchenOrder.builder()
                .id(clientOrder.getId())
                .name(clientOrder.getName())
                .temperature(Temperature.valueOf(clientOrder.getTemp()))
                .price(BigDecimal.valueOf(clientOrder.getPrice()))
                .freshness(Duration.ofSeconds(clientOrder.getFreshness()))
                .createdAt(Instant.now())
                .build();
    }
}
