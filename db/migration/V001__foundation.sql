-- 基础工程自检记录；不占用账户或其他业务表。编号已登记，后续由 D 分配新编号。
CREATE TABLE sar_foundation (
    id SMALLINT NOT NULL,
    version VARCHAR(32) NOT NULL,
    installed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_foundation_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sar_foundation (id, version) VALUES (1, '0.1.0');
