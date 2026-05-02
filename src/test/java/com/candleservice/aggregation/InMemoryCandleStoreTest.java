package com.candleservice.aggregation;

import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;
import com.candleservice.storage.inmemory.InMemoryCandleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCandleStoreTest {

    private InMemoryCandleStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryCandleStore();
    }

    @Test
    @DisplayName("Saved candle is returned by query")
    void save_andQuery_returnsCandle() {
        Candle candle = new Candle(1620000000L, 100.0, 110.0, 90.0, 105.0, 5);
        store.save("BTC-USD", Interval.M1, candle);

        List<Candle> result = store.query(
                "BTC-USD", Interval.M1, 1620000000L, 1620003600L
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).time()).isEqualTo(1620000000L);
    }

    @Test
    @DisplayName("Query returns candles in ascending time order")
    void query_returnsCandles_inAscendingOrder() {
        store.save("BTC-USD", Interval.M1,
                new Candle(1620000120L, 102.0, 112.0, 92.0, 107.0, 6));
        store.save("BTC-USD", Interval.M1,
                new Candle(1620000000L, 100.0, 110.0, 90.0, 105.0, 5));
        store.save("BTC-USD", Interval.M1,
                new Candle(1620000060L, 101.0, 111.0, 91.0, 106.0, 7));

        List<Candle> result = store.query(
                "BTC-USD", Interval.M1, 1620000000L, 1620003600L
        );

        assertThat(result).hasSize(3);
        assertThat(result.get(0).time()).isEqualTo(1620000000L);
        assertThat(result.get(1).time()).isEqualTo(1620000060L);
        assertThat(result.get(2).time()).isEqualTo(1620000120L);
    }

    @Test
    @DisplayName("Query filters by time range correctly")
    void query_filtersByTimeRange() {
        store.save("BTC-USD", Interval.M1,
                new Candle(1620000000L, 100.0, 110.0, 90.0, 105.0, 5));
        store.save("BTC-USD", Interval.M1,
                new Candle(1620000060L, 101.0, 111.0, 91.0, 106.0, 7));
        store.save("BTC-USD", Interval.M1,
                new Candle(1620000120L, 102.0, 112.0, 92.0, 107.0, 6));

        // Query only first two
        List<Candle> result = store.query(
                "BTC-USD", Interval.M1, 1620000000L, 1620000060L
        );

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Query returns empty for unknown symbol")
    void query_returnsEmpty_forUnknownSymbol() {
        List<Candle> result = store.query(
                "UNKNOWN", Interval.M1, 1620000000L, 1620003600L
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Different symbols are stored independently")
    void differentSymbols_storedIndependently() {
        store.save("BTC-USD", Interval.M1,
                new Candle(1620000000L, 65000.0, 65100.0, 64900.0, 65050.0, 10));
        store.save("ETH-USD", Interval.M1,
                new Candle(1620000000L, 3500.0, 3510.0, 3490.0, 3505.0, 8));

        List<Candle> btc = store.query(
                "BTC-USD", Interval.M1, 1620000000L, 1620003600L);
        List<Candle> eth = store.query(
                "ETH-USD", Interval.M1, 1620000000L, 1620003600L);

        assertThat(btc).hasSize(1);
        assertThat(eth).hasSize(1);
        assertThat(btc.get(0).open()).isEqualTo(65000.0);
        assertThat(eth.get(0).open()).isEqualTo(3500.0);
    }
}