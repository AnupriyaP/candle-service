package com.candleservice.aggregation;

import com.candleservice.domain.BidAskEvent;
import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CandleBuilderTest {

    private CandleBuilder builder;

    // Fixed base time — 1 Jan 2024 00:00:00 UTC in milliseconds
    private static final long BASE_TIME = 1704067200000L;

    @BeforeEach
    void setUp() {
        builder = new CandleBuilder("BTC-USD", Interval.M1);
    }

    @Test
    @DisplayName("First event opens a candle but does not close it")
    void firstEvent_opensCandle_doesNotClose() {
        BidAskEvent event = event(BASE_TIME, 65000.0, 65001.0);

        Optional<Candle> result = builder.onEvent(event);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Events in same bucket update OHLC correctly")
    void eventsInSameBucket_updateOhlcCorrectly() {
        // All events within the same 1m bucket
        builder.onEvent(event(BASE_TIME,          65000.0, 65001.0)); // open
        builder.onEvent(event(BASE_TIME + 10000,  65100.0, 65101.0)); // high
        builder.onEvent(event(BASE_TIME + 20000,  64900.0, 64901.0)); // low
        builder.onEvent(event(BASE_TIME + 30000,  65050.0, 65051.0)); // close

        // Send event in next bucket to trigger close
        Optional<Candle> result = builder.onEvent(
                event(BASE_TIME + 60000, 65060.0, 65061.0)
        );

        assertThat(result).isPresent();
        Candle candle = result.get();

        double expectedOpen  = mid(65000.0, 65001.0);
        double expectedHigh  = mid(65100.0, 65101.0);
        double expectedLow   = mid(64900.0, 64901.0);
        double expectedClose = mid(65050.0, 65051.0);

        assertThat(candle.open()).isCloseTo(expectedOpen, within(0.01));
        assertThat(candle.high()).isCloseTo(expectedHigh, within(0.01));
        assertThat(candle.low()).isCloseTo(expectedLow,   within(0.01));
        assertThat(candle.close()).isCloseTo(expectedClose, within(0.01));
        assertThat(candle.volume()).isEqualTo(4);
    }

    @Test
    @DisplayName("New bucket event closes current candle")
    void newBucketEvent_closesCurrentCandle() {
        builder.onEvent(event(BASE_TIME, 65000.0, 65001.0));

        // Event in next 1m bucket
        Optional<Candle> result = builder.onEvent(
                event(BASE_TIME + 60000, 65100.0, 65101.0)
        );

        assertThat(result).isPresent();
        assertThat(result.get().time())
                .isEqualTo(BASE_TIME / 1000L); // UNIX seconds
    }

    @Test
    @DisplayName("Candle time is bucket start in UNIX seconds")
    void candleTime_isBucketStartInUnixSeconds() {
        // Event at 30 seconds into the minute
        builder.onEvent(event(BASE_TIME + 30000, 65000.0, 65001.0));

        Optional<Candle> result = builder.onEvent(
                event(BASE_TIME + 60000, 65100.0, 65101.0)
        );

        assertThat(result).isPresent();
        // Time should be bucket START not event time
        assertThat(result.get().time())
                .isEqualTo(BASE_TIME / 1000L);
    }

    @Test
    @DisplayName("Late events are ignored")
    void lateEvent_isIgnored() {
        // First event opens bucket at BASE_TIME
        builder.onEvent(event(BASE_TIME + 60000, 65000.0, 65001.0));

        // Send event in next bucket to advance
        builder.onEvent(event(BASE_TIME + 120000, 65100.0, 65101.0));

        // Now send a very old event — should be ignored
        Optional<Candle> result = builder.onEvent(
                event(BASE_TIME, 64000.0, 64001.0)
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Single event candle has equal OHLC values")
    void singleEvent_ohlcAreEqual() {
        builder.onEvent(event(BASE_TIME, 65000.0, 65002.0));

        Optional<Candle> result = builder.onEvent(
                event(BASE_TIME + 60000, 65100.0, 65101.0)
        );

        assertThat(result).isPresent();
        Candle candle = result.get();

        double expectedMid = mid(65000.0, 65002.0);
        assertThat(candle.open()).isCloseTo(expectedMid, within(0.01));
        assertThat(candle.high()).isCloseTo(expectedMid, within(0.01));
        assertThat(candle.low()).isCloseTo(expectedMid,  within(0.01));
        assertThat(candle.close()).isCloseTo(expectedMid, within(0.01));
        assertThat(candle.volume()).isEqualTo(1);
    }

    @Test
    @DisplayName("Volume counts all ticks in bucket")
    void volume_countsAllTicks() {
        int tickCount = 10;
        for (int i = 0; i < tickCount; i++) {
            builder.onEvent(event(BASE_TIME + (i * 1000L), 65000.0, 65001.0));
        }

        Optional<Candle> result = builder.onEvent(
                event(BASE_TIME + 60000, 65100.0, 65101.0)
        );

        assertThat(result).isPresent();
        assertThat(result.get().volume()).isEqualTo(tickCount);
    }

    @Test
    @DisplayName("Force flush returns candle when bucket expired")
    void forceFlush_returnsCandle_whenBucketExpired() {
        // Event in a past bucket
        long pastBucket = System.currentTimeMillis() - 120000; // 2 min ago
        builder.onEvent(event(pastBucket, 65000.0, 65001.0));

        Optional<Candle> result = builder.forceFlush();

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Force flush returns empty when no events received")
    void forceFlush_returnsEmpty_whenNoEvents() {
        Optional<Candle> result = builder.forceFlush();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("High is always the maximum mid price")
    void high_isMaximumMidPrice() {
        builder.onEvent(event(BASE_TIME,         64000.0, 64001.0));
        builder.onEvent(event(BASE_TIME + 10000, 66000.0, 66001.0)); // highest
        builder.onEvent(event(BASE_TIME + 20000, 65000.0, 65001.0));

        Optional<Candle> result = builder.onEvent(
                event(BASE_TIME + 60000, 65000.0, 65001.0)
        );

        assertThat(result).isPresent();
        assertThat(result.get().high())
                .isCloseTo(mid(66000.0, 66001.0), within(0.01));
    }

    @Test
    @DisplayName("Low is always the minimum mid price")
    void low_isMinimumMidPrice() {
        builder.onEvent(event(BASE_TIME,         65000.0, 65001.0));
        builder.onEvent(event(BASE_TIME + 10000, 63000.0, 63001.0)); // lowest
        builder.onEvent(event(BASE_TIME + 20000, 65000.0, 65001.0));

        Optional<Candle> result = builder.onEvent(
                event(BASE_TIME + 60000, 65000.0, 65001.0)
        );

        assertThat(result).isPresent();
        assertThat(result.get().low())
                .isCloseTo(mid(63000.0, 63001.0), within(0.01));
    }

    // ─── Helpers ─────────────────────────────────────────────

    private BidAskEvent event(long timestamp, double bid, double ask) {
        return new BidAskEvent("BTC-USD", bid, ask, timestamp);
    }

    private double mid(double bid, double ask) {
        return (bid + ask) / 2.0;
    }
}