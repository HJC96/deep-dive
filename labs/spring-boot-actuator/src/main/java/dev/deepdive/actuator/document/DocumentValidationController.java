package dev.deepdive.actuator.document;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentValidationController {

    private final DocumentValidationService documentValidationService;

    public DocumentValidationController(DocumentValidationService documentValidationService) {
        this.documentValidationService = documentValidationService;
    }

    @PostMapping("/documents/validate")
    public DocumentValidationResult validate(
            @RequestParam(defaultValue = "contract") String documentType,
            @RequestParam(defaultValue = "true") boolean valid
    ) {
        return documentValidationService.validate(documentType, valid);
    }
}
