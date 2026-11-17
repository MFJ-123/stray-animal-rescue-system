-- Receipts and business changes commit together. Never delete receipts while clients may retry.
CREATE TABLE idempotency_receipt (
    actor_id BIGINT NOT NULL,
    action VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (actor_id, action, request_key),
    CONSTRAINT fk_receipt_actor FOREIGN KEY (actor_id) REFERENCES account(id)
) ENGINE=InnoDB;
