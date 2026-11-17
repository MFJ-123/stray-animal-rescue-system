CREATE TABLE account (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 account VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 nickname VARCHAR(20) NOT NULL,
 contact VARCHAR(100) NOT NULL DEFAULT '',
 volunteer_state VARCHAR(16) NOT NULL DEFAULT 'NONE',
 active BOOLEAN NOT NULL DEFAULT TRUE,
 auth_version BIGINT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,
 CHECK (volunteer_state IN ('NONE','ACTIVE','SUSPENDED')),
 CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE role_grant (
 user_id BIGINT NOT NULL,
 role VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(user_id,role),
 FOREIGN KEY(user_id) REFERENCES account(id),
 CHECK(role IN ('SYSTEM_ADMIN','RESCUE_ADMIN','ADOPTION_ADMIN'))
) ENGINE=InnoDB;
CREATE TABLE access_session (
 token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id BIGINT NOT NULL,
 auth_version BIGINT NOT NULL,
 last_activity DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES account(id),
 INDEX idx_session_user(user_id),
 INDEX idx_session_activity(last_activity)
) ENGINE=InnoDB;
CREATE TABLE login_guard (
 guard_key VARCHAR(72) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 attempts INT NOT NULL DEFAULT 0,
 window_start DATETIME(6) NOT NULL,
 locked_until DATETIME(6) NULL
) ENGINE=InnoDB;
CREATE TABLE volunteer_request (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 material VARCHAR(500) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reason VARCHAR(500) NOT NULL DEFAULT '',
 reviewer_id BIGINT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,
 reviewed_at DATETIME(6) NULL,
 pending_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN state='PENDING' THEN user_id ELSE NULL END) STORED,
 UNIQUE KEY uq_volunteer_pending(pending_user_id),
 UNIQUE KEY uq_volunteer_request_key(user_id,request_key),
 FOREIGN KEY(user_id) REFERENCES account(id),
 FOREIGN KEY(reviewer_id) REFERENCES account(id),
 CHECK(state IN ('PENDING','APPROVED','REJECTED')),
 INDEX idx_volunteer_queue(state,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE audit_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 object_type VARCHAR(32) NOT NULL,
 object_id BIGINT NOT NULL,
 action VARCHAR(48) NOT NULL,
 before_state VARCHAR(500) NOT NULL,
 after_state VARCHAR(500) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 request_id VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX idx_audit_object(object_type,object_id,created_at),
 INDEX idx_audit_request(request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- A stable row serializes bootstrap and last-system-administrator protection.
CREATE TABLE access_admin_guard (id SMALLINT PRIMARY KEY, CHECK(id=1)) ENGINE=InnoDB;
INSERT INTO access_admin_guard VALUES (1);
