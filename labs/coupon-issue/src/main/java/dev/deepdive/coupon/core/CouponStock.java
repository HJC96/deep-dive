package dev.deepdive.coupon.core;

import java.util.Objects;

public final class CouponStock {

    private final Long couponId;
    private final int totalQuantity;
    private int remainingQuantity;

    public CouponStock(Long couponId, int totalQuantity) {
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("전체 수량은 음수일 수 없습니다.");
        }
        this.couponId = Objects.requireNonNull(couponId, "쿠폰 ID는 필수입니다.");
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
    }

    public Long couponId() {
        return couponId;
    }

    public int totalQuantity() {
        return totalQuantity;
    }

    public int decrease() {
        int currentQuantity = remainingQuantity;
        Thread.yield();
        remainingQuantity = currentQuantity - 1;
        return remainingQuantity;
    }

    public int remainingQuantity() {
        return remainingQuantity;
    }
}
