package com.candleservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * TradingView Lightweight Charts compatible response format.
 *
 * {
 *   "s": "ok",
 *   "t": [1620000000, ...],
 *   "o": [29500.5, ...],
 *   "h": [29510.0, ...],
 *   "l": [29490.0, ...],
 *   "c": [29505.0, ...],
 *   "v": [10, ...]
 * }
 */
public record HistoryResponse(

        @JsonProperty("s")
        String status,

        @JsonProperty("t")
        List<Long> timestamps,

        @JsonProperty("o")
        List<Double> open,

        @JsonProperty("h")
        List<Double> high,

        @JsonProperty("l")
        List<Double> low,

        @JsonProperty("c")
        List<Double> close,

        @JsonProperty("v")
        List<Long> volume
) {
    /**
     * Factory method for successful response with data.
     */
    public static HistoryResponse ok(List<Long> t,
                                     List<Double> o,
                                     List<Double> h,
                                     List<Double> l,
                                     List<Double> c,
                                     List<Long> v) {
        return new HistoryResponse("ok", t, o, h, l, c, v);
    }

    /**
     * Factory method for empty response — no data in range.
     */
    public static HistoryResponse noData() {
        return new HistoryResponse("no_data",
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    /**
     * Factory method for error response.
     */
    public static HistoryResponse error(String message) {
        return new HistoryResponse("error: " + message,
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }
}