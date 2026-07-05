package dev.deepdive.springtestinfra.dynamicproperty;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@ActiveProfiles("dynamic-property-source")
class DynamicPropertySourceTest {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("dynamic_property_source")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?serverTimezone=UTC");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @Test
    void DynamicPropertySource는_정적_datasource_설정보다_우선해서_테스트컨테이너_DB에_연결한다() throws Exception {
        String datasourceUrl = environment.getRequiredProperty("spring.datasource.url");

        assertThat(datasourceUrl)
                .startsWith("jdbc:mysql://localhost:")
                .contains("/dynamic_property_source")
                .doesNotContain("localhost:1")
                .doesNotContain("this_datasource_should_not_be_used");
        assertThat(environment.getRequiredProperty("spring.datasource.username")).isEqualTo("test");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select database()")) {

            assertThat(connection.isValid(2)).isTrue();
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo("dynamic_property_source");
        }
    }
}
