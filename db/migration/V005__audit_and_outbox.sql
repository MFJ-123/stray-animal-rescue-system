ALTER TABLE audit_log
 ADD COLUMN module VARCHAR(16) NOT NULL DEFAULT 'ACCESS',
 ADD COLUMN corrects_id BIGINT NULL,
 ADD CONSTRAINT fk_audit_correction FOREIGN KEY(corrects_id) REFERENCES audit_log(id),
 ADD INDEX idx_audit_scope(module,created_at,id),
 ADD INDEX idx_audit_time(created_at,id);

CREATE TABLE outbox_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 event_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 object_type VARCHAR(32) NOT NULL,
 object_id BIGINT NOT NULL,
 payload JSON NOT NULL,
 payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0,
 available_at DATETIME(6) NOT NULL,
 lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME(6) NULL,
 last_error VARCHAR(64) NOT NULL DEFAULT '',
 created_at DATETIME(6) NOT NULL,
 completed_at DATETIME(6) NULL,
 UNIQUE KEY uq_outbox_key(event_key),
 INDEX idx_outbox_ready(status,available_at,event_type,id),
 INDEX idx_outbox_lease(status,lease_until,id),
 CHECK(status IN ('PENDING','PROCESSING','RETRY','DEAD','DONE')),
 CHECK(attempts >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
