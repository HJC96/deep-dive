package dev.deepdive.springcache.book;

public record BookSearchCondition(
        String keyword,
        String language
) {
}
