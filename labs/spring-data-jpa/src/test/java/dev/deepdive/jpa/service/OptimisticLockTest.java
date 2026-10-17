package dev.deepdive.jpa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.deepdive.jpa.core.Product;
import dev.deepdive.jpa.monitoring.QueryCountContextHolder;
import dev.deepdive.jpa.monitoring.QueryType;
import dev.deepdive.jpa.monitoring.RequestContext;
import dev.deepdive.jpa.support.MySQLContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * 낙관적 락(@Version) 동작 확인.
 *
 * <p>스레드 없이도 결정적으로 재현하려고 {@link ProductService}의 {@code @Transactional} 메서드로
 * 트랜잭션 경계를 직접 끊는다. "먼저 읽어 둔 옛 스냅샷"과 "그 사이 먼저 커밋한 수정"을 순서대로 만들어 충돌을 일으킨다.
 *
 * <p>detached 엔티티를 {@code saveAndFlush}로 저장하는 두 테스트는 그 호출 동안 실행된 SQL을 종류별로 센다.
 * 쿼리 모니터링용 {@code QueryCountStatementInspector}가 Hibernate에 꽂혀 있으므로, 테스트 스레드에
 * {@link RequestContext}를 직접 심어 두면 HTTP 요청 없이도 같은 방식으로 셀 수 있다.
 */
class OptimisticLockTest extends MySQLContainerTest {

    @Autowired
    private ProductService productService;

    @AfterEach
    void clearSqlCapture() {
        QueryCountContextHolder.clear();
    }

    /** 지금부터 이 스레드에서 실행되는 SQL을 센다. */
    private RequestContext startSqlCapture() {
        RequestContext context = new RequestContext("TEST", "saveAndFlush");
        QueryCountContextHolder.set(context);
        return context;
    }

    @Test
    void 막_저장된_엔티티의_버전은_0이고_수정할_때마다_1씩_증가한다() {
        Long id = productService.create("키보드", 10);

        assertThat(productRepository.findById(id).orElseThrow().version()).isEqualTo(0L);

        // 한 번 수정 → 트랜잭션 커밋 시 UPDATE되며 버전 1
        productService.decreaseStock(id);
        assertThat(productRepository.findById(id).orElseThrow().version()).isEqualTo(1L);

        // 또 수정 → 버전 2
        productService.decreaseStock(id);
        assertThat(productRepository.findById(id).orElseThrow().version()).isEqualTo(2L);
    }

    @Test
    void 충돌_없이_최신_스냅샷으로_저장하면_예외_없이_version이_오르며_성공한다() {
        Long id = productService.create("키보드", 10);

        // 최신 스냅샷(version 0)을 읽는다. 위 충돌 테스트와 달리 그 사이 아무도 건드리지 않는다.
        // getById가 끝나면, 트랜잭션이 종료되기 때문에 fresh가 detached 상태가 된다.
        Product fresh = productService.getById(id);
        assertThat(fresh.version()).isEqualTo(0L);

        // detached 엔티티를 saveAndFlush해도, DB version도 여전히 0이라 버전이 일치 → 정상 커밋.
        RequestContext sql = startSqlCapture();
        productService.decreaseStockAndSave(fresh);
        QueryCountContextHolder.clear();

        // merge가 먼저 SELECT로 현재 행을 읽고(version 0, 일치), 그다음 UPDATE ... WHERE version = 0이 1행을 바꾼다.
        assertThat(sql.countOf(QueryType.SELECT)).isEqualTo(1);
        assertThat(sql.countOf(QueryType.UPDATE)).isEqualTo(1);

        // 충돌이 없으니 예외가 나지 않고 stock 9, version 1.
        // 이 대조군이 있어야 충돌 테스트의 예외가 "낙관적 락 때문"이지 saveAndFlush 자체 탓이 아님이 증명된다.
        Product current = productRepository.findById(id).orElseThrow();
        assertThat(current.stock()).isEqualTo(9);
        assertThat(current.version()).isEqualTo(1L);
    }

    @Test
    void 옛_버전으로_수정하면_먼저_커밋한_쪽이_이기고_나중_쪽은_낙관적_락_예외가_난다() {
        Long id = productService.create("키보드", 10);

        // 사용자 A가 먼저 읽어 둔 스냅샷 (version 0). 트랜잭션이 끝나 detached 상태가 된다.
        Product staleA = productService.getById(id);
        assertThat(staleA.version()).isEqualTo(0L);

        // 사용자 B가 그 사이 수정을 먼저 끝낸다 → DB version 0 → 1, stock 10 → 9
        productService.decreaseStock(id);

        // 사용자 A가 자신의 옛 스냅샷(version 0)으로 수정 시도 → DB는 이미 version 1 → 버전 불일치
        // saveAndFlush가 detached 엔티티를 merge하면서 version 0 vs 1을 비교해 예외를 던진다.
        RequestContext sql = startSqlCapture();
        assertThatThrownBy(() -> productService.decreaseStockAndSave(staleA))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        QueryCountContextHolder.clear();

        // 예외는 UPDATE의 영향받은 행 수가 0이라서 난 게 아니다. UPDATE는 아예 실행되지 않았다.
        // merge가 SELECT로 읽어 온 DB version(1)과 스냅샷 version(0)을 메모리에서 비교해 그 자리에서 거부한다.
        assertThat(sql.countOf(QueryType.SELECT)).isEqualTo(1);
        assertThat(sql.countOf(QueryType.UPDATE)).isZero();

        // B의 수정만 반영(stock 9). A까지 반영됐다면 8이었을 것 → 9라는 건 A가 거부됐다는 뜻.
        Product current = productRepository.findById(id).orElseThrow();
        assertThat(current.stock()).isEqualTo(9);
        assertThat(current.version()).isEqualTo(1L);
    }
}
