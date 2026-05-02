package com.candleservice.api;

import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;
import com.candleservice.storage.CandleStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 6,
        topics = {"market.bidask"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "candle.symbols=BTC-USD,ETH-USD,SOL-USD,BNB-USD",
        "candle.intervals=1s,5s,1m,15m,1h",
        "candle.simulator.enabled=false",
        "candle.flusher.rate-ms=1000",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "API_KEY_1=dev-key-abc123",           // ADD this line
        "spring.security.user.name=test",
        "spring.security.user.password=test"
})
class HistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CandleStore candleStore;

    private static final String API_KEY = "dev-key-abc123";

    @Test
    @WithMockUser
    @DisplayName("Returns 200 with candle data when data exists")
    void getHistory_returnsData_whenCandlesExist() throws Exception {
        List<Candle> candles = List.of(
                new Candle(1620000000L, 29500.0, 29510.0, 29490.0, 29505.0, 10),
                new Candle(1620000060L, 29505.0, 29515.0, 29500.0, 29510.0, 8)
        );

        when(candleStore.query(anyString(), any(Interval.class), anyLong(), anyLong()))
                .thenReturn(candles);

        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m")
                        .param("from", "1620000000")
                        .param("to", "1620003600")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value("ok"))
                .andExpect(jsonPath("$.t[0]").value(1620000000))
                .andExpect(jsonPath("$.t[1]").value(1620000060))
                .andExpect(jsonPath("$.o[0]").value(29500.0))
                .andExpect(jsonPath("$.h[0]").value(29510.0))
                .andExpect(jsonPath("$.l[0]").value(29490.0))
                .andExpect(jsonPath("$.c[0]").value(29505.0))
                .andExpect(jsonPath("$.v[0]").value(10));
    }

    @Test
    @WithMockUser
    @DisplayName("Returns no_data when no candles in range")
    void getHistory_returnsNoData_whenNoCandlesExist() throws Exception {
        when(candleStore.query(anyString(), any(Interval.class), anyLong(), anyLong()))
                .thenReturn(List.of());

        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value("no_data"));
    }

    @Test
    @WithMockUser
    @DisplayName("Returns 400 for unknown interval")
    void getHistory_returns400_forUnknownInterval() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "99x")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.s").value("error: Unknown interval: 99x"));
    }

    @Test
    @DisplayName("Returns 401 when API key is missing")
    void getHistory_returns401_whenApiKeyMissing() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Returns 401 when API key is invalid")
    void getHistory_returns401_whenApiKeyInvalid() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m")
                        .header("X-API-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("Returns 400 when from is after to")
    void getHistory_returns400_whenFromAfterTo() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m")
                        .param("from", "1620003600")
                        .param("to", "1620000000")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest());
    }
}