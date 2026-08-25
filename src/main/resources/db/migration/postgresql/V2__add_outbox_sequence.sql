CREATE TABLE outbox_sequence (
    name       VARCHAR(50) PRIMARY KEY,
    last_value BIGINT NOT NULL
);

INSERT INTO outbox_sequence (name, last_value) VALUES ('global', 0);

ALTER TABLE outbox ADD COLUMN sequence_no BIGINT;
ALTER TABLE outbox ALTER COLUMN sequence_no SET NOT NULL;
ALTER TABLE outbox ADD CONSTRAINT outbox_sequence_no_unique UNIQUE (sequence_no);

CREATE INDEX outbox_pending_fifo
    ON outbox (sequence_no)
    WHERE published_at IS NULL;
