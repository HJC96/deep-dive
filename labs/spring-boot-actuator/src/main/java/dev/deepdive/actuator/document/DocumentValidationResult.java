package dev.deepdive.actuator.document;

public record DocumentValidationResult(String documentType, boolean valid, String result) {
}
