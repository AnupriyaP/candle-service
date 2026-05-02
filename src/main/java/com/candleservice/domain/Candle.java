package com.candleservice.domain;

/**
 * Represents a completed OHLC candlestick.
 *
 * time   — bucket start time in UNIX seconds (not millis)
 *           TradingView expects seconds not milliseconds
 * volume — number of ticks in this candle (synthetic volume)
 */
public record Candle(
        long time,      // UNIX seconds — bucket start
        double open,    // first mid price in bucket
        double high,    // highest mid price in bucket
        double low,     // lowest mid price in bucket
        double close,   // last mid price in bucket
        long volume     // number of ticks
) {}