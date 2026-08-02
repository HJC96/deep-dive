package dev.deepdive.springtestinfra.archunit.example.service;

import dev.deepdive.springtestinfra.archunit.example.repository.SampleRepository;
import org.springframework.stereotype.Service;

@Service
public class SampleService {

    private final SampleRepository sampleRepository;

    public SampleService(SampleRepository sampleRepository) {
        this.sampleRepository = sampleRepository;
    }

    public String greet() {
        return sampleRepository.findMessage();
    }
}
