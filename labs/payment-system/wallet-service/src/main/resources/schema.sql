-- wallet-service 스키마. payment-service처럼 이 DDL로 직접 관리한다.

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
    idempotency_key VARCHAR(255)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_wallet_transactions_wallet_id (wallet_id),
    KEY idx_wallet_transactions_idempotency_key (idempotency_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
