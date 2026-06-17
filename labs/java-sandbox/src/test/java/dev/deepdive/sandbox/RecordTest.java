package dev.deepdive.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordTest {

    /*
     * record는 생성자 인자 목록(component)이 곧 상태가 되는 값 중심 타입이다.
     * 접근자, equals/hashCode, toString은 자동으로 만들어지고,
     * compact constructor로 생성 시점의 검증과 정규화를 넣을 수 있다.
     *
     * DTO, Command, VO처럼 생성 후 바뀌지 않는 값 묶음에는 잘 맞는다.
     * 정적 팩토리 메서드를 추가해서 생성 의도에 이름을 붙일 수도 있다.
     * 반대로 Entity/JPA 객체처럼 식별자, 변경 추적, 프록시가 중요한 타입에는 보통 맞지 않는다.
     */

    // -------------------------------------------------------------------------
    // DTO에서 Command로 변환
    // -------------------------------------------------------------------------

    record CreateOrderRequest(Long memberId, List<OrderLineRequest> lines) {

        CreateOrderCommand toCommand() {
            return new CreateOrderCommand(
                    memberId,
                    lines.stream()
                            .map(OrderLineRequest::toCommandLine)
                            .toList()
            );
        }
    }

    record OrderLineRequest(Long productId, int quantity) {

        CreateOrderCommand.OrderLine toCommandLine() {
            return new CreateOrderCommand.OrderLine(productId, quantity);
        }
    }

    record CreateOrderCommand(Long memberId, List<OrderLine> lines) {

        static CreateOrderCommand singleLine(Long memberId, Long productId, int quantity) {
            return new CreateOrderCommand(
                    memberId,
                    List.of(OrderLine.of(productId, quantity))
            );
        }

        CreateOrderCommand {
            if (memberId == null) {
                throw new IllegalArgumentException("memberId is required");
            }

            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException("order lines are required");
            }

            // List 같은 mutable component는 record가 참조만 들고 있으면 외부 변경에 같이 흔들린다.
            // 새 immutable List로 복사해서 "외부 원본 리스트"와 "record 내부 리스트"를 분리한다.
            lines = List.copyOf(lines);
        }

        record OrderLine(Long productId, int quantity) {

            static OrderLine of(Long productId, int quantity) {
                return new OrderLine(productId, quantity);
            }

            OrderLine {
                if (productId == null) {
                    throw new IllegalArgumentException("productId is required");
                }

                if (quantity <= 0) {
                    throw new IllegalArgumentException("quantity must be positive");
                }
            }
        }
    }

    @Test
    void 요청_DTO를_record로_만들고_command로_매핑할_수_있다() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                List.of(
                        new OrderLineRequest(100L, 2),
                        new OrderLineRequest(200L, 1)
                )
        );

        CreateOrderCommand command = request.toCommand();

        assertThat(command.memberId()).isEqualTo(1L);
        assertThat(command.lines()).containsExactly(
                new CreateOrderCommand.OrderLine(100L, 2),
                new CreateOrderCommand.OrderLine(200L, 1)
        );
    }

    @Test
    void 팩토리_메서드로_생성_가능하다() {
        CreateOrderCommand command = CreateOrderCommand.singleLine(1L, 100L, 2);

        assertThat(command).isEqualTo(new CreateOrderCommand(
                1L,
                List.of(new CreateOrderCommand.OrderLine(100L, 2))
        ));
    }

    @Test
    void command도_record로_만들고_생성_시점에_규칙을_검증할_수_있다() {
        assertThatThrownBy(() -> new CreateOrderCommand(
                null,
                List.of(new CreateOrderCommand.OrderLine(100L, 1))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId is required");

        assertThatThrownBy(() -> new CreateOrderCommand(1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("order lines are required");

        assertThatThrownBy(() -> new CreateOrderCommand.OrderLine(100L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be positive");
    }

    @Test
    void 생성_후_원본_컬렉션을_바꿔도_record_내부_상태는_변하지_않는다() {
        List<CreateOrderCommand.OrderLine> lines = new ArrayList<>();
        lines.add(new CreateOrderCommand.OrderLine(100L, 1));

        // command 생성 시점에 List.copyOf(lines)로 복사되므로,
        // command는 아래의 원본 lines와 다른 리스트를 보게 된다.
        CreateOrderCommand command = new CreateOrderCommand(1L, lines);

        // 생성 후 원본 리스트를 바꿔도 command 내부 리스트에는 영향을 주면 안 된다.
        lines.add(new CreateOrderCommand.OrderLine(200L, 1));

        // 방어적 복사가 없다면 200L 상품도 같이 들어와서 이 검증이 실패한다.
        assertThat(command.lines()).containsExactly(new CreateOrderCommand.OrderLine(100L, 1));
    }

    @Test
    void lines_메서드가_반환한_컬렉션은_수정할_수_없다() {
        CreateOrderCommand command = new CreateOrderCommand(
                1L,
                List.of(new CreateOrderCommand.OrderLine(100L, 1))
        );

        // record의 lines() 메서드가 반환한 리스트도 command 밖에서 수정할 수 없어야 한다.
        assertThatThrownBy(() -> command.lines().add(new CreateOrderCommand.OrderLine(300L, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // -------------------------------------------------------------------------
    // VO로서의 record
    // -------------------------------------------------------------------------

    record EmailAddress(String value) {

        static EmailAddress of(String value) {
            return new EmailAddress(value);
        }

        EmailAddress {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("email is required");
            }

            value = value.strip().toLowerCase();

            if (!value.contains("@")) {
                throw new IllegalArgumentException("invalid email");
            }
        }
    }

    record Money(String currency, long amount) {

        static Money of(String currency, long amount) {
            return new Money(currency, amount);
        }

        static Money won(long amount) {
            return new Money("KRW", amount);
        }

        Money {
            if (currency == null || currency.isBlank()) {
                throw new IllegalArgumentException("currency is required");
            }

            if (amount < 0) {
                throw new IllegalArgumentException("amount must not be negative");
            }
        }

        Money add(Money other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("currency must be same");
            }

            return new Money(currency, amount + other.amount);
        }
    }

    @Test
    void VO는_값이_같으면_같은_객체로_비교된다() {
        EmailAddress first = EmailAddress.of("  USER@example.com ");
        EmailAddress second = EmailAddress.of("user@EXAMPLE.com");

        assertThat(first).isNotSameAs(second);
        assertThat(first).isEqualTo(second);
        assertThat(first.value()).isEqualTo("user@example.com");
    }

    @Test
    void VO도_팩토리_메서드로_자주_쓰는_생성_규칙을_표현할_수_있다() {
        Money price = Money.won(1_000);
        Money shippingFee = Money.of("KRW", 3_000);

        assertThat(price).isEqualTo(new Money("KRW", 1_000));
        assertThat(shippingFee).isEqualTo(new Money("KRW", 3_000));
    }

    @Test
    void VO는_생성_시점에_도메인_규칙을_보호한다() {
        assertThatThrownBy(() -> EmailAddress.of("not-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid email");

        assertThatThrownBy(() -> Money.won(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must not be negative");
    }

    @Test
    void VO는_기존_값을_바꾸는_대신_새_값을_반환한다() {
        Money current = new Money("KRW", 1_000);

        Money added = current.add(new Money("KRW", 500));

        assertThat(current).isEqualTo(new Money("KRW", 1_000));
        assertThat(added).isEqualTo(new Money("KRW", 1_500));
        assertThatThrownBy(() -> current.add(new Money("USD", 500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currency must be same");
    }
}
