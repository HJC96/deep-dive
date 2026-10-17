-- ledger-service 스키마. 복식부기 장부. 이 DDL로 직접 관리한다.
-- 기동 시 spring.sql.init.mode=always 로 매번 실행된다. 모든 문장은 여러 번 실행해도 안전해야 한다
-- (IF NOT EXISTS, ON DUPLICATE KEY, DROP TRIGGER IF EXISTS 후 CREATE). 트리거 본문은 한 문장이라 구분자를 바꿀 필요가 없다.
-- 장부는 append-only: 트리거로 UPDATE/DELETE를 막는다. 차변 합 == 대변 합은 저장 전에 도메인(Ledger.verifyBalanced)과
-- 어댑터(JpaLedgerEntryRepository)에서 확인한다.
-- 장부에 적을 주문 라인은 결제 이벤트 payload 로 받으므로 payment_orders 복제 테이블은 두지 않는다.

CREATE TABLE IF NOT EXISTS accounts
(
    id   BIGINT       NOT NULL COMMENT '고정 계정 id (자동 증가 아님, 시딩)',
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 복식부기 대상 계정. JpaAccountRepository 가 이름으로 조회한다(REVENUE=대변, ITEM_BUYER=차변).
INSERT INTO accounts (id, name)
VALUES (1, 'REVENUE'),
       (2, 'ITEM_BUYER')
ON DUPLICATE KEY UPDATE name = VALUES(name);

CREATE TABLE IF NOT EXISTS ledger_transactions
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    description     VARCHAR(255) NOT NULL,
    reference_id    BIGINT       NOT NULL,
    reference_type  VARCHAR(50)  NOT NULL,
    order_id        VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL COMMENT 'orderId + reference_type + reference_id 로 만든 키',
    PRIMARY KEY (id),
    KEY idx_ledger_transactions_order_id (order_id),
    -- 중복 기록의 최종 방어선. 조회 후 저장(check-then-act)만으로는 동시에 들어온 중복 메시지를 막지 못한다.
    UNIQUE KEY uk_ledger_transactions_idempotency_key (idempotency_key)
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
