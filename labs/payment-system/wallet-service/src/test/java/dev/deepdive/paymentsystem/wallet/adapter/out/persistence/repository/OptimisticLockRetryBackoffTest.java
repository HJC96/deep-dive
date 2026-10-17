package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimisticLockRetryBackoffTest {

    @Test
    void delay_grows_exponentially_until_the_cap() {
        OptimisticLockRetryBackoff upperBound = new OptimisticLockRetryBackoff(50, 1000, () -> 1.0);

        assertThat(upperBound.delayMillis(1)).isEqualTo(50);
        assertThat(upperBound.delayMillis(2)).isEqualTo(100);
        assertThat(upperBound.delayMillis(3)).isEqualTo(200);
        assertThat(upperBound.delayMillis(5)).isEqualTo(800);
        assertThat(upperBound.delayMillis(6)).isEqualTo(1000);
        assertThat(upperBound.delayMillis(60)).isEqualTo(1000);
    }

    @Test
    void jitter_keeps_at_least_half_of_the_exponential_delay() {
        OptimisticLockRetryBackoff lowerBound = new OptimisticLockRetryBackoff(50, 1000, () -> 0.0);

        assertThat(lowerBound.delayMillis(1)).isEqualTo(25);
        assertThat(lowerBound.delayMillis(3)).isEqualTo(100);
        assertThat(lowerBound.delayMillis(10)).isEqualTo(500);
    }

    @Test
    void random_delays_stay_within_the_jitter_window() {
        OptimisticLockRetryBackoff backoff = new OptimisticLockRetryBackoff(50, 1000, Math::random);

        for (int i = 0; i < 1000; i++) {
            assertThat(backoff.delayMillis(3)).isBetween(100L, 200L);
        }
    }

    @Test
    void retry_number_starts_from_one() {
        OptimisticLockRetryBackoff backoff = new OptimisticLockRetryBackoff(50, 1000, () -> 0.5);

        assertThatThrownBy(() -> backoff.delayMillis(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wallet_repository_retries_a_bounded_number_of_times() {
        assertThat(JpaWalletRepository.MAX_RETRIES).isEqualTo(3);
        assertThat(JpaWalletRepository.MAX_DELAY_MILLIS).isGreaterThanOrEqualTo(JpaWalletRepository.BASE_DELAY_MILLIS);
    }
}
