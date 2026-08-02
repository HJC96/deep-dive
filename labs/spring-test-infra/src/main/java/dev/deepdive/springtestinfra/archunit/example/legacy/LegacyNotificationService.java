package dev.deepdive.springtestinfra.archunit.example.legacy;

import dev.deepdive.springtestinfra.archunit.example.repository.SampleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LegacyNotificationService {

    @Autowired
    private SampleRepository sampleRepository;

    public String sendNotification() {
        return sampleRepository.findMessage();
    }
}
