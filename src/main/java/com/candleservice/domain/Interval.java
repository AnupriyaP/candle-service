package com.candleservice.domain;

import java.util.Arrays;
import java.util.Optional;

public enum Interval {

    S1("1s",   1_000L),
    S5("5s",   5_000L),
    M1("1m",  60_000L),
    M15("15m", 900_000L),
    H1("1h",  3_600_000L);

    public final String label;   // what the API receives e.g. "1m"
    public final long millis;    // duration in milliseconds

    Interval(String label, long millis) {
        this.label = label;
        this.millis = millis;
    }

    /**
     * Given a timestamp in milliseconds, returns the start
     * of the bucket it belongs to.
     *
     * Example: timestamp = 61500ms, interval = 1m (60000ms)
     * bucket  = (61500 / 60000) * 60000 = 60000ms
     */
    public long bucketOf(long timestampMs) {
        return (timestampMs / millis) * millis;
    }

    /**
     * Parse interval label from API request.
     * Returns empty if label is unknown.
     */
    public static Optional<Interval> fromLabel(String label) {
        return Arrays.stream(values())
                .filter(i -> i.label.equalsIgnoreCase(label))
                .findFirst();
    }

    @Override
    public String toString() {
        return label;
    }
}