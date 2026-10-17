-- wallet-service 스키마. payment-service처럼 이 DDL로 직접 관리한다.
-- 기동 시 spring.sql.init.mode=always 로 매번 실행되므로 모든 문장은 여러 번 실행해도 안전해야 한다(IF NOT EXISTS).
-- 정산에 필요한 주문 라인은 결제 이벤트 payload 로 받으므로 payment_orders 복제 테이블은 두지 않는다.

CREATE TABLE IF NOT EXISTS wallets
(
    id      BIGINT         NOT NULL AUTO_INCREMENT,
    user_id BIGINT         NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
    version INT            NOT NULL DEFAULT 0 COMMENT '낙관적 락',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallets_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS wallet_transactions
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    wallet_id       BIGINT         NOT NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    type            VARCHAR(10)    NOT NULL COMMENT 'CREDIT / DEBIT',
    order_id        VARCHAR(255)   NOT NULL,
    reference_type  VARCHAR(50)    NOT NULL,
    reference_id    BIGINT         NOT NULL,
    idempotency_key VARCHAR(255)   NOT NULL COMMENT 'orderId + reference_type + reference_id + type 로 만든 키',
    PRIMARY KEY (id),
    KEY idx_wallet_transactions_wallet_id (wallet_id),
    KEY idx_wallet_transactions_order_id (order_id),
    -- 중복 정산의 최종 방어선. 조회 후 저장(check-then-act)만으로는 동시에 들어온 중복 메시지를 막지 못한다.
    UNIQUE KEY uk_wallet_transactions_idempotency_key (idempotency_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
