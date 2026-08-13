package dev.deepdive.transaction.tcc.seat;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 좌석 데이터베이스 한 벌. DataSource·EntityManagerFactory·TransactionManager를 직접 만든다.
 *
 * <p>지갑 쪽에 같은 구성이 한 벌 더 있고 둘은 서로를 모른다. 이게 이 실험실의 전제다. 한 트랜잭션이
 * 두 데이터베이스를 아우르지 않고, 참여자가 각자 자기 로컬 트랜잭션만 커밋한다.
 *
 * <p>{@code basePackages}를 이 패키지로 좁혀 두었으므로 여기 리포지토리는 전부
 * {@code seatTransactionManager}를 쓴다. 지갑 엔티티는 스캔 대상에 들어오지 않는다.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "dev.deepdive.transaction.tcc.seat",
        entityManagerFactoryRef = "seatEntityManagerFactory",
        transactionManagerRef = "seatTransactionManager")
public class SeatJpaConfig {

    private static final String PACKAGE = "dev.deepdive.transaction.tcc.seat";

    @Bean
    public DataSource seatDataSource(
            @Value("${tcc.seat.datasource.url}") String url,
            @Value("${tcc.seat.datasource.username}") String username,
            @Value("${tcc.seat.datasource.password}") String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean seatEntityManagerFactory(
            @Qualifier("seatDataSource") DataSource dataSource) {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(PACKAGE);
        factory.setPersistenceUnitName("seat");
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        return factory;
    }

    @Bean
    public PlatformTransactionManager seatTransactionManager(
            @Qualifier("seatEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
