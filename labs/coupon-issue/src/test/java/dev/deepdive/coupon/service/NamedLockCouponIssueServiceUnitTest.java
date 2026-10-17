package dev.deepdive.coupon.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.deepdive.coupon.repository.CouponRepository;
import dev.deepdive.coupon.repository.NamedLockRepository;
import org.junit.jupiter.api.Test;

// GET_LOCK 실패(타임아웃 0, 에러 NULL)를 DB 없이 재현하는 단위 테스트.
class NamedLockCouponIssueServiceUnitTest {

    private final NamedLockRepository namedLockRepository = mock(NamedLockRepository.class);
    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final NamedLockCouponIssueService self = mock(NamedLockCouponIssueService.class);
    private final NamedLockCouponIssueService service =
            new NamedLockCouponIssueService(namedLockRepository, couponRepository, self);

    @Test
    void GET_LOCK이_타임아웃_0을_반환하면_차감하지_않고_실패한다() {
        when(namedLockRepository.getLock("1")).thenReturn(0);

        assertThatThrownBy(() -> service.issue(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("네임드 락 획득에 실패했습니다");

        verifyNoInteractions(self, couponRepository);
        verify(namedLockRepository, never()).releaseLock(anyString());
    }

    @Test
    void GET_LOCK이_NULL을_반환하면_차감하지_않고_실패한다() {
        when(namedLockRepository.getLock("1")).thenReturn(null);

        assertThatThrownBy(() -> service.issue(1L))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(self, couponRepository);
        verify(namedLockRepository, never()).releaseLock(anyString());
    }

    @Test
    void 락을_획득하면_차감_후_해제한다() {
        when(namedLockRepository.getLock("1")).thenReturn(1);

        service.issue(1L);

        verify(self).issueOnce(1L);
        verify(namedLockRepository).releaseLock("1");
    }
}
