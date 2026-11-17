-- =============================================================================
-- Migration V3: Add Non-Clustered Performance Indexes for Hot-Path Queries
-- =============================================================================

-- 1. Orders: Composite index for user portfolio & ledger queries (filter + sort)
CREATE INDEX IF NOT EXISTS idx_orders_userid_status_datetime_id
    ON orders (userid, status, datetime ASC, id ASC);

-- 2. Orders: Partial unique index on idempotency_key for fast replay lookups
CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_idempotency_key
    ON orders (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 3. MarketData: Composite index for latest-price lookups (single & batch IN queries)
CREATE INDEX IF NOT EXISTS idx_marketdata_instrumentid_date_id
    ON marketdata (instrumentid, date DESC, id DESC);

-- 4. Instruments: Functional case-insensitive index + standard ticker index
CREATE INDEX IF NOT EXISTS idx_instruments_ticker_lower
    ON instruments (LOWER(ticker));

CREATE INDEX IF NOT EXISTS idx_instruments_ticker
    ON instruments (ticker);

