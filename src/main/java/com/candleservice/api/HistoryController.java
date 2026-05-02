package com.candleservice.api;

import com.candleservice.api.dto.HistoryResponse;
import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;
import com.candleservice.storage.CandleStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/history")
@Tag(name = "Market History", description = "OHLC candlestick data for charting")
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

    private final CandleStore candleStore;

    public HistoryController(CandleStore candleStore) {
        this.candleStore = candleStore;
    }

    @GetMapping
    @Operation(
            summary = "Fetch historical candles",
            description = "Returns OHLC data in TradingView Lightweight Charts format"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Data returned"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    })
    public ResponseEntity<HistoryResponse> getHistory(

            @Parameter(description = "Symbol e.g. BTC-USD", required = true, example = "BTC-USD")
            @RequestParam String symbol,

            @Parameter(description = "Interval: 1s, 5s, 1m, 15m, 1h", required = true, example = "1m")
            @RequestParam String interval,

            @Parameter(description = "From UNIX seconds", example = "1620000000")
            @RequestParam(required = false) Long from,

            @Parameter(description = "To UNIX seconds", example = "1620003600")
            @RequestParam(required = false) Long to
    ) {
        log.info("History request symbol={} interval={} from={} to={}",
                symbol, interval, from, to);

        Optional<Interval> intervalOpt = Interval.fromLabel(interval);
        if (intervalOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(HistoryResponse.error("Unknown interval: " + interval));
        }

        long now = System.currentTimeMillis() / 1000L;
        long fromSeconds = (from != null) ? from : now - 86400;
        long toSeconds   = (to   != null) ? to   : now;

        if (fromSeconds >= toSeconds) {
            return ResponseEntity.badRequest()
                    .body(HistoryResponse.error("'from' must be before 'to'"));
        }

        List<Candle> candles = candleStore.query(
                symbol, intervalOpt.get(), fromSeconds, toSeconds);

        if (candles.isEmpty()) {
            return ResponseEntity.ok(HistoryResponse.noData());
        }

        List<Long>   t = candles.stream().map(Candle::time).toList();
        List<Double> o = candles.stream().map(Candle::open).toList();
        List<Double> h = candles.stream().map(Candle::high).toList();
        List<Double> l = candles.stream().map(Candle::low).toList();
        List<Double> c = candles.stream().map(Candle::close).toList();
        List<Long>   v = candles.stream().map(Candle::volume).toList();

        return ResponseEntity.ok(HistoryResponse.ok(t, o, h, l, c, v));
    }
}