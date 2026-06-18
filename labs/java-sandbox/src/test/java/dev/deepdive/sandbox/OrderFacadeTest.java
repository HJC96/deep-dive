package dev.deepdive.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.sandbox.OrderFacade.DeliveryService;
import dev.deepdive.sandbox.OrderFacade.NotificationService;
import dev.deepdive.sandbox.OrderFacade.OrderCommand;
import dev.deepdive.sandbox.OrderFacade.OrderResult;
import dev.deepdive.sandbox.OrderFacade.PaymentGateway;
import dev.deepdive.sandbox.OrderFacade.StockService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderFacadeTest {

    /*
     * Facade Pattern
     *
     * 클라이언트가 여러 하위 시스템의 호출 순서와 세부 구현을 직접 알지 않도록,
     * 단순한 진입점 하나로 복잡한 작업 흐름을 감싸는 패턴이다.
     */

    @Test
    void 파사드는_복잡한_하위_시스템을_단순한_메서드로_감싼다() {
        List<String> events = new ArrayList<>();
        OrderFacade orderFacade = new OrderFacade(
                new StockService(events),
                new PaymentGateway(events),
                new DeliveryService(events),
                new NotificationService(events)
        );

        OrderResult result = orderFacade.placeOrder(new OrderCommand(
                1L,
                100L,
                2,
                30_000
        ));

        assertThat(result).isEqualTo(new OrderResult("payment-1", "delivery-1"));
        assertThat(events).containsExactly(
                "stock reserved: productId=100, quantity=2",
                "payment approved: memberId=1, amount=30000",
                "delivery requested: memberId=1, reservationId=reservation-100",
                "notification sent: memberId=1, deliveryId=delivery-1"
        );
    }
}
