-- ledger-service 스키마. 복식부기 장부. 이 DDL로 직접 관리한다.
-- 장부는 append-only: 트리거로 UPDATE/DELETE를 막는다. 차변·대변 합이 0인지는 도메인(DoubleLedgerEntry)에서 강제한다.

CREATE TABLE IF NOT EXISTS accounts
(
    id   BIGINT       NOT NULL COMMENT '고정 계정 id (자동 증가 아님, 시딩)',
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_transactions
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    description     VARCHAR(255) NOT NULL,
    reference_id    BIGINT       NOT NULL,
    reference_type  VARCHAR(50)  NOT NULL,
    order_id        VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ledger_transactions_order_id (order_id),
    KEY idx_ledger_transactions_idempotency_key (idempotency_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_entries
(
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    amount         DECIMAL(19, 2) NOT NULL,
    account_id     BIGINT         NOT NULL,
    transaction_id BIGINT         NOT NULL,
    type           VARCHAR(10)    NOT NULL COMMENT 'CREDIT / DEBIT',
    PRIMARY KEY (id),
    KEY idx_ledger_entries_transaction_id (transaction_id),
    KEY idx_ledger_entries_account_id (account_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 불변 장부: 기록된 행은 수정·삭제 불가.
DROP TRIGGER IF EXISTS trg_ledger_transactions_no_update;
CREATE TRIGGER trg_ledger_transactions_no_update BEFORE UPDATE ON ledger_transactions
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger_transactions is append-only';

DROP TRIGGER IF EXISTS trg_ledger_transactions_no_delete;
CREATE TRIGGER trg_ledger_transactions_no_delete BEFORE DELETE ON ledger_transactions
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger_transactions is append-only';

DROP TRIGGER IF EXISTS trg_ledger_entries_no_update;
CREATE TRIGGER trg_ledger_entries_no_update BEFORE UPDATE ON ledger_entries
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger_entries is append-only';

DROP TRIGGER IF EXISTS trg_ledger_entries_no_delete;
CREATE TRIGGER trg_ledger_entries_no_delete BEFORE DELETE ON ledger_entries
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger_entries is append-only';
