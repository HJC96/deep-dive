package dev.deepdive.actuator.file;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileProcessingController {

    private final FileProcessingService fileProcessingService;

    public FileProcessingController(FileProcessingService fileProcessingService) {
        this.fileProcessingService = fileProcessingService;
    }

    @PostMapping("/files/convert")
    public FileConversionResult convert(
            @RequestParam(defaultValue = "monthly-report.csv") String fileName,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        return fileProcessingService.convertFile(fileName, format);
    }
}
