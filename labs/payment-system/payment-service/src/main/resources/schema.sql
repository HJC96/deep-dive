-- payment-service 스키마. 원본 강의처럼 ORM(ddl-auto)이 아니라 이 DDL로 직접 관리한다.

CREATE TABLE IF NOT EXISTS payment_events
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    buyer_id        BIGINT       NOT NULL,
    order_id        VARCHAR(255) NOT NULL,
    order_name      VARCHAR(255) NOT NULL,
    payment_key     VARCHAR(255) NULL,
    type            VARCHAR(20)  NULL COMMENT 'PaymentType (NORMAL 등)',
    method          VARCHAR(20)  NULL COMMENT 'PaymentMethod (EASY_PAY 등)',
    psp_raw_data    TEXT         NULL COMMENT 'PSP 승인 응답 원본',
    approved_at     DATETIME     NULL,
    is_payment_done TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_events_order_id (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS payment_orders
(
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    payment_event_id     BIGINT         NOT NULL,
    seller_id            BIGINT         NOT NULL,
    product_id           BIGINT         NOT NULL,
    order_id             VARCHAR(255)   NOT NULL,
    amount               DECIMAL(12, 2) NOT NULL,
    payment_order_status VARCHAR(20)    NOT NULL COMMENT 'NOT_STARTED / EXECUTING / SUCCESS / FAILURE / UNKNOWN',
    ledger_updated       TINYINT(1)     NOT NULL DEFAULT 0,
    wallet_updated       TINYINT(1)     NOT NULL DEFAULT 0,
    failed_count         TINYINT        NOT NULL DEFAULT 0,
    threshold            TINYINT        NOT NULL DEFAULT 5 COMMENT '재시도 허용 횟수',
    created_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_payment_orders_order_id (order_id),
    KEY idx_payment_orders_payment_event_id (payment_event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS payment_order_histories
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    payment_order_id BIGINT       NOT NULL,
    previous_status  VARCHAR(20)  NOT NULL,
    new_status       VARCHAR(20)  NOT NULL,
    reason           VARCHAR(255) NOT NULL COMMENT 'PAYMENT_CONFIRMATION_START / PAYMENT_CONFIRMATION_DONE / 실패 사유 등',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_payment_order_histories_payment_order_id (payment_order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
