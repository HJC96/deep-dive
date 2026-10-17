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
        // 검증이 끝나기 전에는 결과를 모른다. 도중에 예외가 나면 이 값이 그대로 tag가 된다.
        String result = "error";

        try {
            boolean passed = callExternalValidator(documentType, valid);
            result = passed ? "success" : "fail";
            return new DocumentValidationResult(documentType, passed, result);
        } finally {
            // 결과를 알고 난 뒤 result tag를 정해서 같은 timer 이름에 success/fail/error를 분리한다.
            Timer timer = Timer.builder("app.document.validation.time")
                    .description("외부 문서 검증 작업 소요 시간")
                    .tag("result", result)
                    .register(meterRegistry);
            sample.stop(timer);
        }
    }

    /**
     * 외부 검증 시스템 호출을 흉내 낸다. {@code valid}는 외부 시스템이 돌려줄 판정이다.
     *
     * <p>문서 종류가 비어 있으면 외부 시스템이 요청을 처리하지 못한 것으로 보고 예외를 던진다.
     */
    private boolean callExternalValidator(String documentType, boolean valid) {
        if (documentType == null || documentType.isBlank()) {
            throw new IllegalArgumentException("문서 종류가 비어 있어 검증할 수 없다.");
        }
        sleep(valid ? 350 : 600);
        return valid;
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
