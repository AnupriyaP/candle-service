package com.candleservice.storage.inmemory;

import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;
import com.candleservice.storage.CandleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Component
@Profile("!timescale")
public class InMemoryCandleStore implements CandleStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCandleStore.class);

    private final Map<String, NavigableMap<Long, Candle>> store =
            new ConcurrentHashMap<>();

    @Override
    public void save(String symbol, Interval interval, Candle candle) {
        String key = buildKey(symbol, interval);
        store.computeIfAbsent(key, k -> new ConcurrentSkipListMap<>())
                .put(candle.time(), candle);
        log.debug("Stored candle in memory key={} time={}", key, candle.time());
    }

    @Override
    public List<Candle> query(String symbol,
                              Interval interval,
                              long fromSeconds,
                              long toSeconds) {
        String key = buildKey(symbol, interval);
        NavigableMap<Long, Candle> candles = store.get(key);

        if (candles == null) {
            return List.of();
        }

        return new ArrayList<>(
                candles.subMap(fromSeconds, true, toSeconds, true).values()
        );
    }

    private String buildKey(String symbol, Interval interval) {
        return symbol + "::" + interval.label;
    }
}