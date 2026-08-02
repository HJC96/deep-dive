package dev.deepdive.springtestinfra.archunit.example.controller;

import dev.deepdive.springtestinfra.archunit.example.service.SampleService;
import org.springframework.stereotype.Controller;

@Controller
public class SampleController {

    private final SampleService sampleService;

    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    public String sample() {
        return sampleService.greet();
    }
}
