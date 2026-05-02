package com.candleservice.aggregation;

import com.candleservice.domain.BidAskEvent;
import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;
import com.candleservice.storage.CandleStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AggregationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AggregationDispatcher.class);

    private final CandleStore candleStore;

    @Value("${candle.symbols}")
    private List<String> symbols;

    @Value("${candle.intervals}")
    private List<String> intervalLabels;

    private final Map<String, CandleBuilder> builders = new ConcurrentHashMap<>();

    public AggregationDispatcher(CandleStore candleStore) {
        this.candleStore = candleStore;
    }

    @PostConstruct
    public void init() {
        for (String symbol : symbols) {
            for (String label : intervalLabels) {
                Interval interval = Interval.fromLabel(label)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Unknown interval: " + label));
                String key = buildKey(symbol, interval);
                builders.put(key, new CandleBuilder(symbol, interval));
                log.info("Initialized CandleBuilder symbol={} interval={}",
                        symbol, interval);
            }
        }
        log.info("AggregationDispatcher ready — {} builders initialized",
                builders.size());
    }

    public void dispatch(BidAskEvent event) {
        for (Interval interval : Interval.values()) {
            String key = buildKey(event.symbol(), interval);
            CandleBuilder builder = builders.get(key);

            if (builder == null) {
                log.warn("No builder found for key={}", key);
                continue;
            }

            Optional<Candle> completed = builder.onEvent(event);
            completed.ifPresent(candle -> {
                candleStore.save(event.symbol(), interval, candle);
                log.info("Saved candle symbol={} interval={} time={}",
                        event.symbol(), interval, candle.time());
            });
        }
    }

    public Map<String, CandleBuilder> getBuilders() {
        return builders;
    }

    public CandleBuilder getBuilder(String symbol, Interval interval) {
        return builders.get(buildKey(symbol, interval));
    }

    private String buildKey(String symbol, Interval interval) {
        return symbol + "::" + interval.label;
    }
}