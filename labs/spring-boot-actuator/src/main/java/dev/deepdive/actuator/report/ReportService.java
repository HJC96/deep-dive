package dev.deepdive.actuator.report;

import io.micrometer.core.annotation.Timed;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    // @Timed는 메서드 전체 실행 시간을 AOP로 감싼 뒤 timer metric으로 기록한다.
    @Timed(value = "app.report.generation.time", description = "리포트 생성 작업 소요 시간")
    public ReportResult generate(String reportType) {
        sleep(750);
        return new ReportResult(reportType, "GENERATED");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Report generation was interrupted.", exception);
        }
    }
}
