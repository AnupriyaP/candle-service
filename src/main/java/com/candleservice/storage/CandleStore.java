package com.candleservice.storage;

import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;

import java.util.List;

/**
 * Interface for candle persistence.
 * Swap implementations via Spring profiles:
 *
 * default        → InMemoryCandleStore
 * timescale      → TimescaleCandleStore
 */
public interface CandleStore {

    /**
     * Save a completed candle.
     */
    void save(String symbol, Interval interval, Candle candle);

    /**
     * Fetch candles for a symbol/interval between two UNIX second timestamps.
     * Results must be ordered by time ascending.
     */
    List<Candle> query(String symbol, Interval interval, long fromSeconds, long toSeconds);
}