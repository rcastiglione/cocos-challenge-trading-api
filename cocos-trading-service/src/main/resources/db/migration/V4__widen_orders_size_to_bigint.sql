-- =============================================================================
-- Migration V4: Widen orders.size from INTEGER (int4) to BIGINT (int8)
-- Protects against integer overflow on high-nominality instruments (COME, AL30).
-- =============================================================================

ALTER TABLE orders
    ALTER COLUMN size TYPE BIGINT;

