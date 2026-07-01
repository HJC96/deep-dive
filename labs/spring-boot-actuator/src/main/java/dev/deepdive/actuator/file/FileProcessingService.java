package dev.deepdive.actuator.file;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class FileProcessingService {

    private final Timer fileConversionTimer;

    public FileProcessingService(MeterRegistry meterRegistry) {
        // Timer는 호출 횟수(COUNT)와 소요 시간(TOTAL_TIME, MAX)을 함께 기록한다.
        this.fileConversionTimer = Timer.builder("app.file.conversion.time")
                .description("파일 변환 작업 소요 시간")
                .register(meterRegistry);
    }

    public FileConversionResult convertFile(String fileName, String format) {
        // record 안에서 실행된 doConvert 시간이 app.file.conversion.time에 쌓인다.
        return fileConversionTimer.record(() -> doConvert(fileName, format));
    }

    private FileConversionResult doConvert(String fileName, String format) {
        // Timer 값을 눈으로 확인하기 쉽도록 의도적으로 2초짜리 작업을 만든다.
        sleep(2_000);
        return new FileConversionResult(fileName, format, "CONVERTED");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("File conversion was interrupted.", exception);
        }
    }
}
