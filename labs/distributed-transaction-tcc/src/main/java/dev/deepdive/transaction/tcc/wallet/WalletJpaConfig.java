package dev.deepdive.transaction.tcc.wallet;

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
 * 지갑 데이터베이스 한 벌. 좌석 쪽과 완전히 분리된 별개의 MySQL 서버를 본다.
 *
 * <p>두 설정이 서로를 모른다는 게 핵심이다. 한 트랜잭션 매니저가 두 데이터베이스를 아우르지 않으므로
 * 좌석 커밋과 지갑 커밋 사이에는 아무 원자성도 없다. 그 틈을 Try·Confirm·Cancel이 메운다.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "dev.deepdive.transaction.tcc.wallet",
        entityManagerFactoryRef = "walletEntityManagerFactory",
        transactionManagerRef = "walletTransactionManager")
public class WalletJpaConfig {

    private static final String PACKAGE = "dev.deepdive.transaction.tcc.wallet";

    @Bean
    public DataSource walletDataSource(
            @Value("${tcc.wallet.datasource.url}") String url,
            @Value("${tcc.wallet.datasource.username}") String username,
            @Value("${tcc.wallet.datasource.password}") String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean walletEntityManagerFactory(
            @Qualifier("walletDataSource") DataSource dataSource) {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(PACKAGE);
        factory.setPersistenceUnitName("wallet");
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        return factory;
    }

    @Bean
    public PlatformTransactionManager walletTransactionManager(
            @Qualifier("walletEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
