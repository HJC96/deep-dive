package dev.deepdive.transaction.tcc;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * 참여자 빈을 올리기 위한 부트 진입점.
 *
 * <p>DataSource가 둘이라 자동 구성이 기본 한 벌을 만들어 주지 못한다. 어설프게 끼어들지 않도록 아예
 * 끄고, {@code seat}·{@code wallet} 패키지의 설정 클래스가 각자 한 벌씩 직접 만든다.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
public class TccApplication {
}
