package dev.deepdive.springtestinfra.archunit.example.repository;

import org.springframework.stereotype.Repository;

@Repository
public class SampleRepository {

    public String findMessage() {
        return "hello";
    }
}
