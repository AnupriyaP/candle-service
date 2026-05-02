package com.candleservice.aggregation;

import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;
import com.candleservice.storage.CandleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class StaleCandleFlusher {

    private static final Logger log = LoggerFactory.getLogger(StaleCandleFlusher.class);

    private final AggregationDispatcher dispatcher;
    private final CandleStore candleStore;

    public StaleCandleFlusher(AggregationDispatcher dispatcher,
                              CandleStore candleStore) {
        this.dispatcher = dispatcher;
        this.candleStore = candleStore;
    }

    @Scheduled(fixedRateString = "${candle.flusher.rate-ms:1000}")
    public void flush() {
        Map<String, CandleBuilder> builders = dispatcher.getBuilders();

        for (Map.Entry<String, CandleBuilder> entry : builders.entrySet()) {
            String key = entry.getKey();
            CandleBuilder builder = entry.getValue();

            Optional<Candle> stale = builder.forceFlush();
            stale.ifPresent(candle -> {
                String[] parts = key.split("::");
                String symbol = parts[0];
                String intervalLabel = parts[1];

                Interval interval = Interval.fromLabel(intervalLabel)
                        .orElseThrow();

                candleStore.save(symbol, interval, candle);
                log.info("Flushed stale candle symbol={} interval={} time={}",
                        symbol, intervalLabel, candle.time());
            });
        }
    }
}