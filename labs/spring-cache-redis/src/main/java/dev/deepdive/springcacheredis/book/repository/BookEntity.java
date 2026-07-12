package dev.deepdive.springcacheredis.book.repository;

import dev.deepdive.springcacheredis.book.domain.Book;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "books")
public class BookEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    protected BookEntity() {
    }

    private BookEntity(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public static BookEntity from(Book book) {
        return new BookEntity(book.id(), book.name());
    }

    public Book toBook() {
        return new Book(id, name);
    }
}
