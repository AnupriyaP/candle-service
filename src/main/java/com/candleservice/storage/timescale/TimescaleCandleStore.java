package com.candleservice.storage.timescale;

import com.candleservice.domain.Candle;
import com.candleservice.domain.Interval;
import com.candleservice.storage.CandleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
@Profile("timescale")
public class TimescaleCandleStore implements CandleStore {

    private static final Logger log = LoggerFactory.getLogger(TimescaleCandleStore.class);

    private final JdbcTemplate jdbc;

    public TimescaleCandleStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String UPSERT_SQL = """
            INSERT INTO candles (symbol, interval, time, open, high, low, close, volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol, interval, time)
            DO UPDATE SET
                open   = EXCLUDED.open,
                high   = EXCLUDED.high,
                low    = EXCLUDED.low,
                close  = EXCLUDED.close,
                volume = EXCLUDED.volume
            """;

    private static final String QUERY_SQL = """
            SELECT time, open, high, low, close, volume
            FROM candles
            WHERE symbol   = ?
              AND interval  = ?
              AND time BETWEEN ? AND ?
            ORDER BY time ASC
            """;

    @Override
    public void save(String symbol, Interval interval, Candle candle) {
        Timestamp time = Timestamp.from(Instant.ofEpochSecond(candle.time()));
        jdbc.update(UPSERT_SQL,
                symbol, interval.label, time,
                candle.open(), candle.high(),
                candle.low(), candle.close(),
                candle.volume());
        log.debug("Upserted candle symbol={} interval={} time={}",
                symbol, interval.label, candle.time());
    }

    @Override
    public List<Candle> query(String symbol,
                              Interval interval,
                              long fromSeconds,
                              long toSeconds) {
        Timestamp from = Timestamp.from(Instant.ofEpochSecond(fromSeconds));
        Timestamp to   = Timestamp.from(Instant.ofEpochSecond(toSeconds));
        return jdbc.query(QUERY_SQL, this::mapRow, symbol, interval.label, from, to);
    }

    private Candle mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Candle(
                rs.getTimestamp("time").toInstant().getEpochSecond(),
                rs.getDouble("open"),
                rs.getDouble("high"),
                rs.getDouble("low"),
                rs.getDouble("close"),
                rs.getLong("volume")
        );
    }
}