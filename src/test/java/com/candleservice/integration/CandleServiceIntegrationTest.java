package com.candleservice.integration;

import com.candleservice.domain.BidAskEvent;
import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;
import com.candleservice.ingestion.kafka.BidAskProducer;
import com.candleservice.storage.CandleStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@EmbeddedKafka(
        partitions = 6,
        topics = {"market.bidask"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "candle.symbols=BTC-USD,ETH-USD,SOL-USD,BNB-USD",
        "candle.intervals=1s,5s,1m,15m,1h",
        "candle.simulator.enabled=false",
        "candle.flusher.rate-ms=500",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "API_KEY_1=dev-key-abc123",           // ADD this line
        "spring.security.user.name=test",
        "spring.security.user.password=test",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=candle-aggregator-test"
})
class CandleServiceIntegrationTest {

    @Autowired
    private BidAskProducer producer;

    @Autowired
    private CandleStore candleStore;

    @Autowired
    private MockMvc mockMvc;

    private static final String API_KEY = "dev-key-abc123";

    @Test
    @DisplayName("Event published to Kafka is aggregated into a candle")
    void publishedEvent_isAggregatedIntoCandle() {
        // Align to start of a past minute bucket
        // This guarantees all events within 30s stay in the same 1m bucket
        long nowMs = System.currentTimeMillis();
        long bucketStartMs = (nowMs / 60_000) * 60_000;  // start of current minute
        long pastBucketMs = bucketStartMs - 120_000;       // 2 minutes ago

        // All events within first 30s of that past bucket
        producer.publish(new BidAskEvent("BTC-USD", 65000.0, 65002.0, pastBucketMs));
        producer.publish(new BidAskEvent("BTC-USD", 65100.0, 65102.0, pastBucketMs + 10_000));
        producer.publish(new BidAskEvent("BTC-USD", 64900.0, 64902.0, pastBucketMs + 20_000));
        producer.publish(new BidAskEvent("BTC-USD", 65050.0, 65052.0, pastBucketMs + 30_000));

        long fromSeconds = (pastBucketMs / 1000) - 60;
        long toSeconds   = System.currentTimeMillis() / 1000;

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<Candle> candles = candleStore.query(
                            "BTC-USD", Interval.M1, fromSeconds, toSeconds
                    );
                    // Wait until we have a candle with all 4 events
                    assertThat(candles).isNotEmpty();
                    assertThat(candles.stream()
                            .anyMatch(c -> c.volume() == 4)).isTrue();
                });

        List<Candle> candles = candleStore.query(
                "BTC-USD", Interval.M1, fromSeconds, toSeconds
        );

        // Find the candle with all 4 events
        Candle candle = candles.stream()
                .filter(c -> c.volume() == 4)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No candle with 4 events found"));

        assertThat(candle.open()).isCloseTo(65001.0,  within(1.0));
        assertThat(candle.high()).isCloseTo(65101.0,  within(1.0));
        assertThat(candle.low()).isCloseTo(64901.0,   within(1.0));
        assertThat(candle.close()).isCloseTo(65051.0, within(1.0));
        assertThat(candle.volume()).isEqualTo(4);
    }

    @Test
    @WithMockUser
    @DisplayName("Aggregated candle is accessible via REST API")
    void aggregatedCandle_isAccessibleViaApi() throws Exception {
        long pastTime = System.currentTimeMillis() - 120_000;

        producer.publish(new BidAskEvent("ETH-USD", 3500.0, 3502.0, pastTime));
        producer.publish(new BidAskEvent("ETH-USD", 3510.0, 3512.0, pastTime + 10_000));

        long fromSeconds = (pastTime / 1000) - 120;
        long toSeconds   = System.currentTimeMillis() / 1000;

        // Wait for candle to be stored
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<Candle> candles = candleStore.query(
                            "ETH-USD", Interval.M1, fromSeconds, toSeconds
                    );
                    assertThat(candles).isNotEmpty();
                });

        // Call the REST API
        mockMvc.perform(get("/history")
                        .param("symbol", "ETH-USD")
                        .param("interval", "1m")
                        .param("from", String.valueOf(fromSeconds))
                        .param("to",   String.valueOf(toSeconds))
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value("ok"))
                .andExpect(jsonPath("$.t").isArray())
                .andExpect(jsonPath("$.o").isArray())
                .andExpect(jsonPath("$.v").isArray());
    }

    @Test
    @DisplayName("Multiple symbols aggregated independently")
    void multipleSymbols_aggregatedIndependently() {
        long pastTime = System.currentTimeMillis() - 120_000;

        producer.publish(new BidAskEvent("SOL-USD", 150.0, 150.2, pastTime));
        producer.publish(new BidAskEvent("BNB-USD", 600.0, 600.2, pastTime));

        long fromSeconds = (pastTime / 1000) - 120;
        long toSeconds   = System.currentTimeMillis() / 1000;

        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<Candle> sol = candleStore.query(
                            "SOL-USD", Interval.M1, fromSeconds, toSeconds);
                    List<Candle> bnb = candleStore.query(
                            "BNB-USD", Interval.M1, fromSeconds, toSeconds);

                    assertThat(sol).isNotEmpty();
                    assertThat(bnb).isNotEmpty();
                });

        List<Candle> sol = candleStore.query(
                "SOL-USD", Interval.M1, fromSeconds, toSeconds);
        List<Candle> bnb = candleStore.query(
                "BNB-USD", Interval.M1, fromSeconds, toSeconds);

        // Prices must not be mixed between symbols
        assertThat(sol.get(0).open()).isCloseTo(150.1, within(1.0));
        assertThat(bnb.get(0).open()).isCloseTo(600.1, within(1.0));
    }

    @Test
    @WithMockUser
    @DisplayName("Returns 401 when API key missing in integration context")
    void returns401_whenApiKeyMissing() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("Returns no_data for symbol with no events")
    void returnsNoData_forSymbolWithNoEvents() throws Exception {
        long now = System.currentTimeMillis() / 1000;

        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1h")
                        .param("from", String.valueOf(now - 3600))
                        .param("to",   String.valueOf(now))
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value("no_data"));
    }
}