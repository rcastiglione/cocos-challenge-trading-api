-- =============================================================================
-- Migration V2: Add idempotency_key (UNIQUE) and request_hash to orders table
-- =============================================================================

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS idempotency_key UUID,
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(256);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_orders_idempotency_key'
    ) THEN
        ALTER TABLE orders
            ADD CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key);
    END IF;
END $$;

