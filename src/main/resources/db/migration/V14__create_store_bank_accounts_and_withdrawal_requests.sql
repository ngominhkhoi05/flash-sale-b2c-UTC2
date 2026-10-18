-- V14: Create store_bank_accounts and wallet_withdrawal_requests tables

CREATE TABLE IF NOT EXISTS store_bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL UNIQUE REFERENCES stores(id) ON DELETE CASCADE,
    bank_name VARCHAR(100) NOT NULL,
    bank_code VARCHAR(20) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    account_holder VARCHAR(100) NOT NULL,
    branch VARCHAR(150),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_store_bank_accounts_store_id ON store_bank_accounts(store_id);

CREATE TABLE IF NOT EXISTS wallet_withdrawal_requests (
    id BIGSERIAL PRIMARY KEY,
    request_code VARCHAR(50) NOT NULL UNIQUE,
    wallet_id BIGINT NOT NULL REFERENCES wallets(id),
    store_id BIGINT NOT NULL REFERENCES stores(id),
    amount NUMERIC(15, 2) NOT NULL CHECK (amount >= 50000.00),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    bank_name VARCHAR(100) NOT NULL,
    bank_code VARCHAR(20) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    account_holder VARCHAR(100) NOT NULL,
    branch VARCHAR(150),
    seller_note VARCHAR(255),
    admin_note VARCHAR(255),
    idempotency_key VARCHAR(100) UNIQUE,
    approved_by BIGINT REFERENCES users(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    rejected_by BIGINT REFERENCES users(id),
    rejected_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_withdrawal_wallet_status ON wallet_withdrawal_requests(wallet_id, status);
CREATE INDEX IF NOT EXISTS idx_withdrawal_status ON wallet_withdrawal_requests(status);
CREATE INDEX IF NOT EXISTS idx_withdrawal_store_id ON wallet_withdrawal_requests(store_id);
