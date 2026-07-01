package dev.deepdive.actuator.content;

import io.micrometer.core.annotation.Timed;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ContentSearchService {

    // percentiles를 켜면 app.content.search.time.percentile metric에서 p50/p95/p99를 조회할 수 있다.
    @Timed(
            value = "app.content.search.time",
            description = "콘텐츠 검색 API 응답 시간",
            percentiles = {0.5, 0.95, 0.99}
    )
    public ContentSearchResult search(String keyword) {
        // keyword별로 지연 시간을 다르게 만들어 percentile 차이를 관찰한다.
        sleep(delayFor(keyword));
        return new ContentSearchResult(keyword, List.of(
                keyword + " 실습 노트",
                keyword + " 운영 체크리스트",
                keyword + " 장애 분석 기록"
        ));
    }

    private long delayFor(String keyword) {
        return switch (keyword.length() % 3) {
            case 0 -> 120;
            case 1 -> 450;
            default -> 900;
        };
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Content search was interrupted.", exception);
        }
    }
}
