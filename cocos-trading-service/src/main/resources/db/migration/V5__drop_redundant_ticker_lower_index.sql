-- =============================================================================
-- Migration V5: Drop Redundant Functional Index on LOWER(ticker)
-- =============================================================================
-- Tickers are stored in canonical uppercase; idx_instruments_ticker on (ticker)
-- is sufficient for exact-match index scans without LOWER(ticker) overhead.
DROP INDEX IF EXISTS idx_instruments_ticker_lower;

