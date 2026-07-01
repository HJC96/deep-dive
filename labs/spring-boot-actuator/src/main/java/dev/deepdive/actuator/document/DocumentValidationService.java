package dev.deepdive.actuator.document;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class DocumentValidationService {

    private final MeterRegistry meterRegistry;

    public DocumentValidationService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public DocumentValidationResult validate(String documentType, boolean valid) {
        // Timer.Sample은 시작 시점과 기록 시점을 분리할 때 사용한다.
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = valid ? "success" : "fail";

        try {
            sleep(valid ? 350 : 600);
            return new DocumentValidationResult(documentType, valid, result);
        } finally {
            // 결과를 알고 난 뒤 result tag를 정해서 같은 timer 이름에 success/fail을 분리한다.
            Timer timer = Timer.builder("app.document.validation.time")
                    .description("외부 문서 검증 작업 소요 시간")
                    .tag("result", result)
                    .register(meterRegistry);
            sample.stop(timer);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Document validation was interrupted.", exception);
        }
    }
}
