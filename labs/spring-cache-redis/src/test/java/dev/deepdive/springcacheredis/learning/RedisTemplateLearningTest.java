package dev.deepdive.springcacheredis.learning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.springcacheredis.support.RedisAndMySqlContainerTest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RedisTemplate은 먼저 자료형별 API를 선택한 뒤 명령을 호출한다.
 * 이 테스트에서는 opsForValue()로 Redis 문자열 자료형을 다룬다.
 */
class RedisTemplateLearningTest extends RedisAndMySqlContainerTest {

    @Autowired
    private RedisTemplateLearningService redisTemplateLearningService;

    @Test
    void opsForValue로_문자열을_저장하고_조회하고_삭제한다() {
        // 1. Redis에 문자열 저장
        redisTemplateLearningService.save("learning:greeting", "hello redis");

        // 2. 같은 키로 저장된 값 조회
        assertThat(redisTemplateLearningService.find("learning:greeting"))
                .isEqualTo("hello redis");

        // 3. 키 삭제 후에는 조회 결과가 null
        assertThat(redisTemplateLearningService.delete("learning:greeting")).isTrue();
        assertThat(redisTemplateLearningService.find("learning:greeting")).isNull();
    }

    @Test
    void 문자열을_저장할_때_TTL을_함께_설정할_수_있다() {
        // 저장 시 TTL을 함께 설정하면 Redis가 만료 시 키를 자동으로 삭제한다.
        redisTemplateLearningService.saveWithTtl(
                "learning:verification-code",
                "123456",
                Duration.ofSeconds(60)
        );

        assertThat(redisTemplateLearningService.find("learning:verification-code"))
                .isEqualTo("123456");
        assertThat(redisTemplate.getExpire("learning:verification-code", TimeUnit.SECONDS))
                .isBetween(1L, 60L);
    }

    @Test
    void setIfAbsent는_키가_없을_때만_값을_저장한다() {
        // 첫 번째 SET NX는 성공하고, 같은 키에 대한 두 번째 요청은 실패한다.
        boolean firstSaved = redisTemplateLearningService.saveIfAbsent(
                "learning:lock",
                "owner-1",
                Duration.ofSeconds(10)
        );
        boolean secondSaved = redisTemplateLearningService.saveIfAbsent(
                "learning:lock",
                "owner-2",
                Duration.ofSeconds(10)
        );

        assertThat(firstSaved).isTrue();
        assertThat(secondSaved).isFalse();
        assertThat(redisTemplateLearningService.find("learning:lock")).isEqualTo("owner-1");
    }

    @Test
    void opsForHash는_하나의_키_아래_여러_필드를_저장한다() {
        // user:1이라는 하나의 키 아래 name과 email field를 저장한다.
        redisTemplateLearningService.putHash("learning:user:1", "name", "홍길동");
        redisTemplateLearningService.putHash("learning:user:1", "email", "hong@example.com");

        // entries()는 해당 Hash의 모든 field-value 쌍을 반환한다.
        Map<String, String> user = redisTemplateLearningService.findHash("learning:user:1");

        assertThat(user)
                .containsEntry("name", "홍길동")
                .containsEntry("email", "hong@example.com");
    }
}
