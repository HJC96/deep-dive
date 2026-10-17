-- payment-service 스키마. 원본 강의처럼 ORM(ddl-auto)이 아니라 이 DDL로 직접 관리한다.
-- 기동 시 spring.sql.init.mode=always 로 매번 실행되므로 모든 문장은 여러 번 실행해도 안전해야 한다(IF NOT EXISTS 등).

CREATE TABLE IF NOT EXISTS payment_events
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    buyer_id        BIGINT       NOT NULL,
    order_id        VARCHAR(255) NOT NULL,
    order_name      VARCHAR(255) NOT NULL,
    payment_key     VARCHAR(255) NULL,
    type            VARCHAR(20)  NULL COMMENT 'PaymentType (NORMAL / BILLING / BRANDPAY / UNKNOWN)',
    method          VARCHAR(50)  NULL COMMENT 'PaymentMethod 상수 이름 (CARD, CULTURE_GIFT_CERTIFICATE 등, 최장 24자)',
    psp_raw_data    TEXT         NULL COMMENT 'PSP 승인 응답 원본',
    approved_at     DATETIME     NULL,
    is_payment_done TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_events_order_id (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 예전 스키마(method VARCHAR(20))로 만든 DB도 넓힌다. MODIFY 는 다시 실행해도 결과가 같다.
ALTER TABLE payment_events
    MODIFY method VARCHAR(50) NULL COMMENT 'PaymentMethod 상수 이름 (CARD, CULTURE_GIFT_CERTIFICATE 등, 최장 24자)';

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

-- 트랜잭셔널 아웃박스. 결제 승인 커밋과 같은 트랜잭션에서 이벤트를 이 테이블에 쓰고,
-- 릴레이가 status = INIT/FAILURE 인 행을 읽어 Kafka로 발행한다.
-- payload 는 orderId 와 주문 라인(paymentOrders: paymentOrderId, sellerId, productId, amount), totalAmount 를 담는다.
CREATE TABLE IF NOT EXISTS outboxes
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(255) NOT NULL COMMENT '보통 orderId',
    type            VARCHAR(50)  NOT NULL COMMENT 'PaymentEventMessageType (PAYMENT_CONFIRMATION_SUCCESS 등)',
    partition_key   INT          NOT NULL DEFAULT 0,
    payload         JSON         NOT NULL,
    metadata        JSON         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'INIT' COMMENT 'INIT / SUCCESS / FAILURE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outboxes_idempotency_key_type (idempotency_key, type),
    KEY idx_outboxes_status_created_at (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
