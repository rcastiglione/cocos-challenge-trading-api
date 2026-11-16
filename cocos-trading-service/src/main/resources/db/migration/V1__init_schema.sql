-- =============================================================================
-- Cocos Capital Challenge — Initial Relational Schema (V1 Baseline)
-- =============================================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255),
    accountnumber VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS instruments (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(10),
    name VARCHAR(255),
    type VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    instrumentid BIGINT REFERENCES instruments(id),
    userid BIGINT REFERENCES users(id),
    size INTEGER,
    price NUMERIC(10, 2),
    type VARCHAR(10),
    side VARCHAR(10),
    status VARCHAR(20),
    datetime TIMESTAMP
);

CREATE TABLE IF NOT EXISTS marketdata (
    id BIGSERIAL PRIMARY KEY,
    instrumentid BIGINT REFERENCES instruments(id),
    high NUMERIC(10, 2),
    low NUMERIC(10, 2),
    open NUMERIC(10, 2),
    close NUMERIC(10, 2),
    previousclose NUMERIC(10, 2),
    date DATE
);

