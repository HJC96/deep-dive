package dev.deepdive.springcache.book;

public class BookSearchConditionWithoutEquals {

    private final String keyword;
    private final String language;

    public BookSearchConditionWithoutEquals(String keyword, String language) {
        this.keyword = keyword;
        this.language = language;
    }

    public String keyword() {
        return keyword;
    }

    public String language() {
        return language;
    }
}
