package dev.deepdive.springcache.book;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<BookEntity, Long> {

    @Query("select b.name from BookEntity b where b.id = :id")
    Optional<String> findNameById(@Param("id") long id);

    @Query("select max(b.updatedAt) from BookEntity b")
    Optional<Instant> findMaxUpdatedAt();
}
