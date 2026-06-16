package dev.deepdive.coupon.repository;

import dev.deepdive.coupon.core.CouponStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// MySQL 네임드 락(GET_LOCK/RELEASE_LOCK) 전용 리포지토리.
// 락은 row가 아니라 "문자열 키"에 걸리고, 획득/해제는 같은 커넥션(세션)에서 이뤄져야 한다.
public interface NamedLockRepository extends JpaRepository<CouponStock, Long> {

    // 키에 락을 건다. 이미 잡혀 있으면 timeout(초)만큼 대기. 성공 1, 타임아웃 0, 에러 NULL.
    @Query(value = "SELECT GET_LOCK(:key, 10)", nativeQuery = true)
    Integer getLock(@Param("key") String key);

    @Query(value = "SELECT RELEASE_LOCK(:key)", nativeQuery = true)
    Integer releaseLock(@Param("key") String key);
}
