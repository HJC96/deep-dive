package dev.deepdive.springcache.book;

import java.time.Instant;

public record Book(
        long id,
        String name,
        int price,
        Instant updatedAt
) {

    public static final Instant DEFAULT_UPDATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    public Book(long id, String name, int price) {
        this(id, name, price, DEFAULT_UPDATED_AT);
    }
}
