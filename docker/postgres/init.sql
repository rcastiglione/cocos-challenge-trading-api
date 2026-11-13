-- =============================================================================
-- Cocos Capital Challenge — PostgreSQL Schema & Reference Seed Data
-- =============================================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    accountnumber VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS instruments (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    instrumentid BIGINT NOT NULL REFERENCES instruments(id),
    userid BIGINT NOT NULL REFERENCES users(id),
    size INTEGER NOT NULL,
    price NUMERIC(19, 2) NOT NULL,
    type VARCHAR(50) NOT NULL,
    side VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    datetime TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS marketdata (
    id BIGSERIAL PRIMARY KEY,
    instrumentid BIGINT NOT NULL REFERENCES instruments(id),
    high NUMERIC(19, 2),
    low NUMERIC(19, 2),
    open NUMERIC(19, 2),
    close NUMERIC(19, 2),
    previousclose NUMERIC(19, 2),
    date DATE NOT NULL
);

-- =============================================================================
-- Seed Data: Users
-- =============================================================================
INSERT INTO users (id, email, accountnumber) VALUES
    (1, 'emiliano@test.com', '10001'),
    (2, 'jose@test.com', '10002'),
    (3, 'francisco@test.com', '10003'),
    (4, 'juan@test.com', '10004')
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- Seed Data: Instruments (Mandatory 'MONEDA' ARS + 'ACCIONES' Market Tickers)
-- =============================================================================
INSERT INTO instruments (id, ticker, name, type) VALUES
    (1, 'DYCA', 'Dycasa S.A.', 'ACCIONES'),
    (2, 'CAPX', 'Capex S.A.', 'ACCIONES'),
    (3, 'PGR', 'Phoenix Global Resources', 'ACCIONES'),
    (4, 'MOLA', 'Molinos Agro S.A.', 'ACCIONES'),
    (5, 'MIRG', 'Mirgor', 'ACCIONES'),
    (31, 'MOLI', 'Molinos Río de la Plata', 'ACCIONES'),
    (34, 'METR', 'MetroGAS S.A.', 'ACCIONES'),
    (47, 'PAMP', 'Pampa Energía S.A.', 'ACCIONES'),
    (54, 'ALUA', 'Aluar Aluminio Argentino S.A.I.C.', 'ACCIONES'),
    (55, 'BPAT', 'Banco Patagonia', 'ACCIONES'),
    (64, 'LOMA', 'Loma Negra C.I.A.S.A.', 'ACCIONES'),
    (66, 'ARS', 'PESOS', 'MONEDA')
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- Seed Data: MarketData
-- =============================================================================
INSERT INTO marketdata (id, instrumentid, high, low, open, close, previousclose, date) VALUES
    (1, 1, 610.00, 565.00, 575.00, 588.00, 566.00, '2023-07-14'),
    (2, 2, 3295.00, 3220.00, 3235.00, 3238.50, 3249.00, '2023-07-14'),
    (3, 3, 355.00, 345.00, 345.50, 351.50, 345.00, '2023-07-14'),
    (4, 4, 9265.00, 9027.00, 9129.00, 9188.00, 9027.00, '2023-07-14'),
    (5, 5, 19860.00, 19300.00, 19450.00, 19429.50, 19388.50, '2023-07-14'),
    (6, 31, 1320.00, 1280.00, 1295.00, 1310.00, 1290.00, '2023-07-14'),
    (7, 34, 495.00, 466.00, 470.00, 489.50, 465.50, '2023-07-14'),
    (8, 47, 945.00, 905.00, 912.00, 925.85, 906.35, '2023-07-14'),
    (9, 54, 462.00, 448.00, 452.00, 458.50, 450.00, '2023-07-14'),
    (10, 55, 620.00, 600.00, 605.00, 614.00, 602.00, '2023-07-14'),
    (11, 64, 980.00, 950.00, 960.00, 972.00, 955.00, '2023-07-14')
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- Seed Data: Orders (Unified Ledger — CASH_IN, CASH_OUT, BUY, SELL)
-- =============================================================================
INSERT INTO orders (id, instrumentid, userid, size, price, type, side, status, datetime) VALUES
    (1, 66, 1, 1000000, 1.00, 'MARKET', 'CASH_IN', 'FILLED', '2023-07-12 12:11:20'),
    (2, 47, 1, 50, 930.00, 'MARKET', 'BUY', 'FILLED', '2023-07-12 12:31:20'),
    (3, 47, 1, 50, 920.00, 'LIMIT', 'BUY', 'CANCELLED', '2023-07-12 14:21:20'),
    (4, 47, 1, 10, 940.00, 'MARKET', 'SELL', 'FILLED', '2023-07-12 15:14:20'),
    (5, 47, 1, 20, 950.00, 'LIMIT', 'SELL', 'NEW', '2023-07-12 15:34:20'),
    (6, 47, 1, 100, 950.00, 'MARKET', 'SELL', 'REJECTED', '2023-07-12 16:14:20'),
    (7, 31, 1, 50, 1500.00, 'LIMIT', 'BUY', 'NEW', '2023-07-13 11:13:20'),
    (8, 66, 1, 100000, 1.00, 'MARKET', 'CASH_OUT', 'FILLED', '2023-07-13 12:31:20'),
    (9, 31, 1, 20, 1540.00, 'MARKET', 'BUY', 'FILLED', '2023-07-13 14:51:20'),
    (10, 54, 1, 250, 450.00, 'MARKET', 'BUY', 'FILLED', '2023-07-13 16:13:20'),
    (11, 54, 1, 500, 500.00, 'MARKET', 'SELL', 'REJECTED', '2023-07-13 16:28:20')
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- Reset Sequences for Auto-Increment Primary Keys
-- =============================================================================
SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT MAX(id) FROM users), 1), true);
SELECT setval(pg_get_serial_sequence('instruments', 'id'), COALESCE((SELECT MAX(id) FROM instruments), 1), true);
SELECT setval(pg_get_serial_sequence('marketdata', 'id'), COALESCE((SELECT MAX(id) FROM marketdata), 1), true);
SELECT setval(pg_get_serial_sequence('orders', 'id'), COALESCE((SELECT MAX(id) FROM orders), 1), true);

