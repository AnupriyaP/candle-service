package com.candleservice.aggregation;

import com.candleservice.domain.BidAskEvent;
import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;

import java.util.Optional;

public class CandleBuilder {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(CandleBuilder.class);

    private final String symbol;
    private final Interval interval;

    private long currentBucket = -1;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;

    public CandleBuilder(String symbol, Interval interval) {
        this.symbol = symbol;
        this.interval = interval;
    }

    public synchronized Optional<Candle> onEvent(BidAskEvent event) {
        double mid = event.mid();
        long bucket = interval.bucketOf(event.timestamp());

        if (currentBucket == -1) {
            openNewBucket(bucket, mid);
            return Optional.empty();
        }

        if (bucket == currentBucket) {
            updateOhlc(mid);
            return Optional.empty();
        }

        if (bucket > currentBucket) {
            Candle completed = buildCandle();
            log.debug("Closed candle symbol={} interval={} time={} open={} high={} low={} close={} volume={}",
                    symbol, interval, completed.time(),
                    completed.open(), completed.high(),
                    completed.low(), completed.close(),
                    completed.volume());
            openNewBucket(bucket, mid);
            return Optional.of(completed);
        }

        log.warn("Late event ignored symbol={} interval={} eventBucket={} currentBucket={}",
                symbol, interval, bucket, currentBucket);
        return Optional.empty();
    }

    public synchronized Optional<Candle> forceFlush() {
        if (currentBucket == -1 || volume == 0) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        long nowBucket = interval.bucketOf(now);

        if (nowBucket <= currentBucket) {
            return Optional.empty();
        }

        Candle completed = buildCandle();
        log.debug("Force flushed candle symbol={} interval={} time={}",
                symbol, interval, completed.time());

        currentBucket = -1;
        volume = 0;

        return Optional.of(completed);
    }

    private void openNewBucket(long bucket, double price) {
        currentBucket = bucket;
        open  = price;
        high  = price;
        low   = price;
        close = price;
        volume = 1;
    }

    private void updateOhlc(double price) {
        if (price > high) high = price;
        if (price < low)  low  = price;
        close = price;
        volume++;
    }

    private Candle buildCandle() {
        return new Candle(
                currentBucket / 1000L,
                open,
                high,
                low,
                close,
                volume
        );
    }
}