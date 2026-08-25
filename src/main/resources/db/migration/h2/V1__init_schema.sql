CREATE TABLE payments (
    id UUID PRIMARY KEY,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);
