package com.candleservice.domain;

/**
 * Represents a single bid/ask market data tick.
 * This is the raw event that flows through Kafka.
 *
 * mid = (bid + ask) / 2 — used as the OHLC price
 */
public record BidAskEvent(
        String symbol,    // e.g. BTC-USD
        double bid,       // best bid price
        double ask,       // best ask price
        long timestamp    // event time in milliseconds
) {
    public double mid() {
        return (bid + ask) / 2.0;
    }
}