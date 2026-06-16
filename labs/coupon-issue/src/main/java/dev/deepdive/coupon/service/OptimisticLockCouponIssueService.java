package dev.deepdive.coupon.service;

import dev.deepdive.coupon.core.VersionedCouponStock;
import dev.deepdive.coupon.repository.VersionedCouponRepository;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OptimisticLockCouponIssueService {

    private static final long BACKOFF_MILLIS = 5L;

    private final VersionedCouponRepository couponRepository;
    // this vs self
    //  - this  : @Transactional이 덧씌워지기 전의 "원본 객체". this.issueOnce()는 프록시를 우회하므로
    //            @Transactional이 무시되고 트랜잭션이 열리지 않는다(self-invocation 문제).
    //  - self  : Spring이 빈을 감싼 "프록시". self.issueOnce()는 프록시를 거치므로 매 호출마다
    //            새 트랜잭션이 정상적으로 시작된다.
    // 그래서 재시도 루프(issue)에서 issueOnce를 부를 때 this가 아니라 self로 호출해야 한다.
    private final OptimisticLockCouponIssueService self;

    // 왜 생성자로 자기 자신을 다시 주입하나
    //  - this로는 프록시를 부를 수 없으므로(위 참고), 프록시 참조를 따로 손에 쥐어야 한다.
    //    Spring 컨테이너에 "내 타입의 빈"을 요청하면 원본이 아니라 프록시가 주입되므로 self에 그게 담긴다.
    //  - @Lazy가 필요한 이유: 자기 자신을 주입하면 "나를 만들려면 내가 먼저 있어야 한다"는 순환 참조가 생긴다.
    //    @Lazy는 주입을 실제 첫 사용 시점까지 미뤄(지연 프록시) 이 닭-달걀 문제를 깬다.
    public OptimisticLockCouponIssueService(
            VersionedCouponRepository couponRepository,
            @Lazy OptimisticLockCouponIssueService self) {
        this.couponRepository = Objects.requireNonNull(couponRepository, "쿠폰 저장소는 필수입니다.");
        this.self = self;
    }

    // 충돌하면 잠깐 기다렸다가 다시 시도한다. 새 트랜잭션에서 버전을 다시 읽어 재시도해야 하므로
    // 재시도는 트랜잭션 경계 밖(이 메서드)에 있다.
    public void issue(Long couponId) {
        while (true) {
            try {
                self.issueOnce(couponId);
                return;
            } catch (ObjectOptimisticLockingFailureException conflict) {
                sleep(BACKOFF_MILLIS);
            }
        }
    }

    // @Transactional은 왜 issue가 아니라 여기(issueOnce)에 있나
    //  - 낙관적 락 충돌은 메서드 본문이 아니라 "커밋 시점"에 감지된다.
    //  - 만약 재시도 루프가 있는 issue에 @Transactional을 붙이면, 루프 전체가 한 트랜잭션에 갇혀
    //    커밋이 루프가 끝난 뒤에야 일어난다 → 루프 안 catch가 충돌을 잡을 기회가 없고,
    //    충돌난 트랜잭션은 rollback-only라 같은 트랜잭션에서 재시도해도 못 살린다.
    //  - 따라서 "한 번의 시도 = 한 개의 트랜잭션"인 issueOnce에만 @Transactional을 둔다.
    @Transactional
    public void issueOnce(Long couponId) {
        VersionedCouponStock couponStock = couponRepository.findByIdWithOptimisticLock(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 재고를 찾을 수 없습니다. couponId=" + couponId));
        couponStock.decrease();
        couponRepository.save(couponStock);
    } // 이 시점에 커밋됨.

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트되었습니다.", e);
        }
    }
}
