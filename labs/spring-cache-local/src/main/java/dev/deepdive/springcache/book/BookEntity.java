package dev.deepdive.springcache.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "books",
        indexes = @Index(name = "idx_books_updated_at", columnList = "updated_at")
)
public class BookEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp(6)")
    private Instant updatedAt;

    protected BookEntity() {
    }

    private BookEntity(Long id, String name, int price, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.updatedAt = updatedAt;
    }

    static BookEntity from(Book book) {
        return new BookEntity(book.id(), book.name(), book.price(), book.updatedAt());
    }

    Book toBook() {
        return new Book(id, name, price, updatedAt);
    }
}
