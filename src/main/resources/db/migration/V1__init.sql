-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Candles table
CREATE TABLE IF NOT EXISTS candles (
    symbol      TEXT             NOT NULL,
    interval    TEXT             NOT NULL,
    time        TIMESTAMPTZ      NOT NULL,
    open        DOUBLE PRECISION NOT NULL,
    high        DOUBLE PRECISION NOT NULL,
    low         DOUBLE PRECISION NOT NULL,
    close       DOUBLE PRECISION NOT NULL,
    volume      BIGINT           NOT NULL,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

-- Convert to hypertable
SELECT create_hypertable(
    'candles',
    'time',
    if_not_exists => TRUE
);

-- Index for API query pattern
CREATE INDEX IF NOT EXISTS idx_candles_symbol_interval_time
    ON candles (symbol, interval, time DESC);

-- Raw events table
CREATE TABLE IF NOT EXISTS bid_ask_events (
    id          BIGSERIAL        PRIMARY KEY,
    symbol      TEXT             NOT NULL,
    bid         DOUBLE PRECISION NOT NULL,
    ask         DOUBLE PRECISION NOT NULL,
    mid_price   DOUBLE PRECISION GENERATED ALWAYS AS ((bid + ask) / 2) STORED,
    event_time  TIMESTAMPTZ      NOT NULL,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

-- Index for event replay
CREATE INDEX IF NOT EXISTS idx_events_symbol_time
    ON bid_ask_events (symbol, event_time DESC);

-- Compression for old candle data
ALTER TABLE candles SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol, interval',
    timescaledb.compress_orderby   = 'time DESC'
);