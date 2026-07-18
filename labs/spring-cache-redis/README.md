# spring-cache-redis

MySQL/JPA를 원본 저장소로 두고, Spring Cache 추상화와 `StringRedisTemplate` 직접 구현을 비교하며 Redis 캐시 패턴을 검증하는 실험이다.

## 어떤 테스트가 있나?

| 테스트 | 확인 내용 |
| --- | --- |
| `SpringCacheRedisApplicationTest` | Redis/MySQL Testcontainers와 Spring Cache 구성이 함께 시작되는지 확인 |
| `SpringCacheBookServiceTest` | `@Cacheable`의 cache miss/hit, Redis JSON 저장, 빈 `Optional` 미캐싱 |
| `RedisTemplateLearningTest` | 문자열, TTL, `SET NX`, Hash를 `StringRedisTemplate`으로 다루는 방법 |
| `RedisBasicCommandTest` | Redis 기본 명령, sorted set, `INFO` 확인 |
| `RedisTemplateBookCacheTest` | Cache Aside, Null Caching, TTL Jitter |
| `RedisHotKeyLockTest` | 동시 cache miss와 Redis 분산 락 |

## 어떤 환경에서 실행되나?

| 항목 | 내용 |
| --- | --- |
| Spring Boot | 3.4.1 |
| 원본 저장소 | MySQL + Spring Data JPA |
| 캐시 | Redis + Spring Data Redis |
| 캐시 접근 | Spring Cache(`RedisCacheManager`), `StringRedisTemplate` |
| 직렬화 | Jackson JSON(`RedisTemplate`은 직접 변환, Spring Cache는 설정으로 자동 변환) |
| 통합 테스트 | Testcontainers Redis 7.2.5, MySQL 8.0.36 |

Docker가 없으면 컨테이너 기반 테스트는 skip된다.

## `RedisTemplate` API는 어떤 Redis 명령과 연결되나?

`StringRedisTemplate`은 key와 value를 문자열로 직렬화하는 `RedisTemplate`이다. 먼저 Redis 자료형에 맞는 `opsFor...()` API를 고른 뒤 명령을 호출한다.

| Redis 자료형 | `StringRedisTemplate` API | 대표 Redis 명령 |
| --- | --- | --- |
| String | `opsForValue().set()`, `get()` | `SET`, `GET` |
| Hash | `opsForHash().put()`, `entries()` | `HSET`, `HGETALL` |
| List | `opsForList().leftPush()`, `range()` | `LPUSH`, `LRANGE` |
| Set | `opsForSet().add()`, `members()` | `SADD`, `SMEMBERS` |
| Sorted Set | `opsForZSet().add()`, `reverseRange()` | `ZADD`, `ZREVRANGE` |
| 키 삭제 | `delete(key)` | `DEL` |

## Spring Cache 추상화도 Redis와 함께 사용하나?

사용한다. Spring Cache는 공통 캐시 API이고, `RedisCacheManager`가 실제 Redis 조회와 저장을 담당한다.

```java
@Cacheable(
        cacheNames = "books",
        key = "#id",
        unless = "#result == null"
)
public Optional<Book> findBookById(long id) {
    return bookOriginService.findById(id);
}
```

### 호출은 어떻게 흘러가나?

Spring 캐시 프록시가 호출을 먼저 가로챈다.

1. `books` 캐시에서 id를 조회한다.
2. cache hit이면 메서드 본문을 건너뛰고 Redis 값을 반환한다.
3. cache miss이면 메서드로 MySQL을 조회하고 `Book`을 Redis에 저장한다.
4. 조회 결과를 호출자에게 반환한다.

`unless = "#result == null"`은 결과가 없을 때 저장을 건너뛴다. 값이 있으면 `Optional<Book>`에서 `Book`을 꺼내 저장하고, cache hit에서는 다시 `Optional`로 감싼다. `Optional.empty()`의 내부 값은 `null`이므로 캐싱하지 않는다.

### Redis에는 어떤 형태로 저장되나?

Spring Cache도 직렬화가 필요하다. `RedisTemplate` 예제와 달리 서비스가 `ObjectMapper`를 직접 호출하지 않고, [`RedisCacheConfig`](src/main/java/dev/deepdive/springcacheredis/config/RedisCacheConfig.java)가 `RedisCacheManager`의 value serializer를 Jackson JSON으로 설정한다. TTL 10분, null 미캐싱, key prefix는 `application.yml` 설정을 사용한다.

```text
Key:   deep-dive:spring-cache-redis::books::1
Value: {"@class":"dev.deepdive.springcacheredis.book.domain.Book","id":1,"name":"Effective Java"}
TTL:   최대 10분
```

`GenericJackson2JsonRedisSerializer`는 Java 객체를 타입 정보가 포함된 JSON `byte[]`로 직렬화하고 다시 원래 객체로 역직렬화한다. `SerializationPair`는 이 serializer를 Spring Cache의 Writer와 Reader로 연결한다.

```mermaid
sequenceDiagram
    autonumber

    participant CacheManager as RedisCacheManager
    participant Pair as SerializationPair
    participant Serializer as GenericJackson2JsonRedisSerializer
    participant Mapper as ObjectMapper 복사본
    participant Redis as Redis

    Note over CacheManager,Redis: cache miss 결과 저장
    CacheManager->>Pair: Book 직렬화 요청
    Pair->>Serializer: serialize(Book)
    Serializer->>Mapper: Book을 JSON byte[]로 변환
    Mapper-->>Serializer: 타입 정보가 포함된 JSON byte[]
    Serializer-->>Pair: JSON byte[]
    Pair-->>CacheManager: Redis에 저장할 값
    CacheManager->>Redis: SET key JSON

    Note over CacheManager,Redis: cache hit 값 조회
    CacheManager->>Redis: GET key
    Redis-->>CacheManager: JSON byte[]
    CacheManager->>Pair: JSON 역직렬화 요청
    Pair->>Serializer: deserialize(JSON byte[])
    Serializer->>Mapper: JSON과 타입 정보 읽기
    Mapper-->>Serializer: Book 객체
    Serializer-->>Pair: Book 객체
    Pair-->>CacheManager: Book 객체

    Note over Serializer,Mapper: 캐스팅이 아니라 JSON 직렬화와 역직렬화
```

### 언제 `RedisTemplate`을 직접 쓰나?

| 상황 | 주로 선택하는 방식 | 이유 |
| --- | --- | --- |
| 일반적인 메서드 결과 캐싱 | Spring Cache | `@Cacheable`, `@CachePut`, `@CacheEvict`로 반복 코드를 줄일 수 있음 |
| Redis String을 직접 GET/SET | `RedisTemplate` | key, value, TTL을 코드에서 세밀하게 제어할 수 있음 |
| Hash, Set, ZSet 사용 | `RedisTemplate` | Spring Cache는 Redis 자료구조 API를 추상화하지 않음 |
| Null sentinel, TTL Jitter | `RedisTemplate` | 값과 TTL에 별도 정책이 필요함 |
| `SET NX`, Lua, 분산 락 | `RedisTemplate` | Redis의 원자적 명령과 스크립트를 직접 사용해야 함 |

두 방식은 함께 사용한다. 일반 조회는 Spring Cache로 단순화하고, `findWithDistributedLock()`처럼 Redis 고유 기능이 필요할 때 `RedisTemplate`을 사용한다. 기본 `RedisCacheManager`는 non-locking이므로 `@Cacheable`이 분산 락을 대신하지는 않는다.

참고: [Spring Cache 애노테이션 공식 문서](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html), [Spring Data Redis Cache 공식 문서](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html)

## 각 테스트는 어떤 요청 흐름을 검증하나?

### `SpringCacheRedisApplicationTest`는 어떻게 부팅되나?

```mermaid
sequenceDiagram
    autonumber

    box rgba(37, 99, 235, 0.40) 테스트 실행
    participant Test as 테스트
    end

    box rgba(245, 158, 11, 0.40) Spring 설정
    participant Registry as DynamicPropertyRegistry
    participant Context as ApplicationContext
    end

    box rgba(219, 39, 119, 0.40) 저장소
    participant RedisContainer as RedisContainer
    participant MySQLContainer as MySQLContainer
    end

    Test->>RedisContainer: Redis 시작
    Test->>MySQLContainer: MySQL 시작
    RedisContainer-->>Registry: host, mapped port
    MySQLContainer-->>Registry: JDBC URL, 계정
    Registry->>Context: redis / datasource 프로퍼티 등록
    Context->>Context: RedisConnectionFactory 생성
    Context->>Context: RedisCacheManager 생성
    Context->>Context: DataSource / JPA 구성
    Test->>Context: CacheManager, ConnectionFactory 검사
```

### `SpringCacheBookServiceTest`에서 `@Cacheable`은 어떻게 동작하나?

첫 번째 호출과 두 번째 호출은 같은 메서드를 사용한다. 차이는 Spring 캐시 프록시가 첫 번째 cache miss에서는 실제 메서드를 실행하지만, 두 번째 cache hit에서는 실제 메서드를 실행하지 않는다는 점이다.

```mermaid
sequenceDiagram
    autonumber

    box rgba(37, 99, 235, 0.40) 테스트 실행
    participant Test as SpringCacheBookServiceTest
    end

    box rgba(245, 158, 11, 0.40) 앱
    participant Proxy as Spring Cache 프록시
    participant CacheManager as RedisCacheManager
    participant Service as SpringCacheBookService
    participant Origin as BookOriginService
    participant Repository as BookRepository
    end

    box rgba(219, 39, 119, 0.40) 저장소
    participant Redis as Redis
    participant MySQL as MySQL
    end

    Note over Test,MySQL: 첫 번째 호출 - cache miss
    Test->>Proxy: findBookById(1L)
    Proxy->>CacheManager: books 캐시에서 key 1 조회
    CacheManager->>Redis: GET deep-dive:...::books::1
    Redis-->>CacheManager: miss
    CacheManager-->>Proxy: miss
    Proxy->>Service: 실제 메서드 본문 실행
    Service->>Origin: findById(1L)
    Origin->>Repository: findById(1L)
    Repository->>MySQL: SELECT books
    MySQL-->>Repository: BookEntity
    Repository-->>Origin: BookEntity
    Origin-->>Service: Optional<Book>
    Service-->>Proxy: Optional<Book>
    Proxy->>CacheManager: books 캐시에 Book 저장
    CacheManager->>Redis: SET key Book JSON EX 10분
    Proxy-->>Test: Optional<Book>

    Note over Test,Redis: 두 번째 호출 - cache hit
    Test->>Proxy: findBookById(1L)
    Proxy->>CacheManager: books 캐시에서 key 1 조회
    CacheManager->>Redis: GET deep-dive:...::books::1
    Redis-->>CacheManager: Book JSON
    CacheManager-->>Proxy: Book
    Proxy-->>Test: Optional<Book>
    Note over Service,MySQL: 메서드 본문과 MySQL을 다시 호출하지 않아 readCount는 1
```

### `RedisTemplateLearningTest`는 어떤 명령을 실행하나?

`getExpire(key, TimeUnit.SECONDS)`는 value를 조회하는 메서드가 아니라, 해당 key가 자동 삭제되기까지 남은 TTL을 초 단위로 조회하는 메서드다. 저장 직후에도 코드 실행 시간이 흐르므로 60초 TTL은 `60`보다 조금 작은 값으로 조회될 수 있다. 반환값이 `-1`이면 key는 있지만 만료 시간이 없고, `-2`이면 key 자체가 없다는 뜻이다.

```mermaid
sequenceDiagram
    autonumber

    box rgba(37, 99, 235, 0.40) 테스트 실행
    participant Test as RedisTemplateLearningTest
    end

    box rgba(245, 158, 11, 0.40) 앱
    participant Service as RedisTemplateLearningService
    participant Template as StringRedisTemplate
    end

    box rgba(219, 39, 119, 0.40) 저장소
    participant Redis as Redis
    end

    Note over Test,Redis: 문자열 저장 · 조회 · 삭제
    Test->>Service: save("learning:greeting", "hello redis")
    Service->>Template: opsForValue().set(...)
    Template->>Redis: SET learning:greeting "hello redis"
    Test->>Service: find("learning:greeting")
    Service->>Template: opsForValue().get(...)
    Template->>Redis: GET learning:greeting
    Redis-->>Template: "hello redis"
    Template-->>Service: "hello redis"
    Service-->>Test: "hello redis"
    Test->>Service: delete("learning:greeting")
    Service->>Template: delete(...)
    Template->>Redis: DEL learning:greeting

    Note over Test,Redis: TTL과 조건부 저장
    Test->>Service: saveWithTtl(..., 60초)
    Service->>Template: opsForValue().set(..., ttl)
    Template->>Redis: SET learning:verification-code ... EX 60
    Test->>Template: getExpire("learning:verification-code", SECONDS)
    Template->>Redis: TTL learning:verification-code
    Redis-->>Template: 남은 TTL
    Template-->>Test: 1 ~ 60초
    Test->>Service: saveIfAbsent("learning:lock", "owner-1")
    Service->>Template: opsForValue().setIfAbsent(...)
    Template->>Redis: SET learning:lock owner-1 NX EX 10
    Redis-->>Template: true
    Template-->>Service: true
    Service-->>Test: true
    Test->>Service: saveIfAbsent("learning:lock", "owner-2")
    Template->>Redis: SET learning:lock owner-2 NX EX 10
    Redis-->>Template: false
    Template-->>Service: false
    Service-->>Test: false

    Note over Test,Redis: Hash
    Test->>Service: putHash("learning:user:1", "name", "홍길동")
    Service->>Template: opsForHash().put(...)
    Template->>Redis: HSET learning:user:1 name 홍길동
    Test->>Service: putHash("learning:user:1", "email", "hong@example.com")
    Service->>Template: opsForHash().put(...)
    Template->>Redis: HSET learning:user:1 email hong@example.com
    Test->>Service: findHash("learning:user:1")
    Service->>Template: opsForHash().entries(...)
    Template->>Redis: HGETALL learning:user:1
    Redis-->>Template: name, email field-value 쌍
    Template-->>Service: Map<String, String>
    Service-->>Test: Map<String, String>
```

### `RedisBasicCommandTest`는 Redis 기본 명령을 어떻게 확인하나?

```mermaid
sequenceDiagram
    autonumber

    box rgba(37, 99, 235, 0.40) 테스트 실행
    participant Test as RedisBasicCommandTest
    end

    box rgba(219, 39, 119, 0.40) 저장소
    participant Redis as Redis
    end

    Test->>Redis: SET redis:hello world
    Test->>Redis: GET redis:hello
    Redis-->>Test: world
    Test->>Redis: EXISTS redis:hello
    Redis-->>Test: 1
    Test->>Redis: SET redis:temporary value EX 10
    Test->>Redis: TTL redis:temporary
    Redis-->>Test: 1 ~ 10초
    Test->>Redis: SET redis:lock:main-page owner-1 NX EX 5
    Redis-->>Test: 성공
    Test->>Redis: SET redis:lock:main-page owner-2 NX EX 5
    Redis-->>Test: 실패
    Test->>Redis: ZADD book:ranking 90 "Clean Code"
    Test->>Redis: ZADD book:ranking 100 "Effective Java"
    Test->>Redis: ZREVRANGE book:ranking 0 -1
    Redis-->>Test: Effective Java, Clean Code
    Test->>Redis: INFO stats
    Redis-->>Test: total_commands_processed 등
```

### `RedisTemplateBookCacheTest`의 Cache Aside는 어떻게 동작하나?

```mermaid
sequenceDiagram
    autonumber

    box rgba(37, 99, 235, 0.40) 테스트 실행
    participant Test as RedisTemplateBookCacheTest
    end

    box rgba(245, 158, 11, 0.40) 앱
    participant Service as RedisTemplateBookCacheService
    participant Origin as BookOriginService
    participant Repository as BookRepository
    participant Json as ObjectMapper
    end

    box rgba(219, 39, 119, 0.40) 저장소
    participant Redis as Redis
    participant MySQL as MySQL
    end

    Note over Test,MySQL: 첫 조회: cache miss
    Test->>Service: findBookById(1L)
    Service->>Redis: GET book:1
    Redis-->>Service: miss
    Service->>Origin: findById(1L)
    Origin->>Repository: findById(1L)
    Repository->>MySQL: SELECT books
    MySQL-->>Repository: BookEntity
    Repository-->>Origin: BookEntity
    Origin-->>Service: Book
    Service->>Json: writeValueAsString(Book)
    Json-->>Service: JSON
    Service->>Redis: SET book:1 JSON EX 10분
    Service-->>Test: Book

    Note over Test,Redis: 두 번째 조회: cache hit
    Test->>Service: findBookById(1L)
    Service->>Redis: GET book:1
    Redis-->>Service: JSON
    Service->>Json: readValue(JSON, Book.class)
    Json-->>Service: Book
    Service-->>Test: Book
```

### 없는 ID를 반복 조회하면 어떻게 되나?

```mermaid
sequenceDiagram
    autonumber

    box rgba(37, 99, 235, 0.40) 테스트 실행
    participant Test as RedisTemplateBookCacheTest
    end

    box rgba(245, 158, 11, 0.40) 앱
    participant Service as RedisTemplateBookCacheService
    participant Origin as BookOriginService
    participant Repository as BookRepository
    end

    box rgba(219, 39, 119, 0.40) 저장소
    participant Redis as Redis
    participant MySQL as MySQL
    end

    Note over Test,MySQL: 첫 조회
    Test->>Service: findBookByIdWithNullCaching(999L)
    Service->>Redis: GET book:999
    Redis-->>Service: miss
    Service->>Origin: findById(999L)
    Origin->>Repository: findById(999L)
    Repository->>MySQL: SELECT books
    MySQL-->>Repository: 결과 없음
    Repository-->>Origin: Optional.empty()
    Origin-->>Service: Optional.empty()
    Service->>Redis: SET book:999 "__NULL__" EX 30
    Service-->>Test: Optional.empty()

    loop 이후 19번 조회
        Test->>Service: findBookByIdWithNullCaching(999L)
        Service->>Redis: GET book:999
        Redis-->>Service: "__NULL__"
        Service-->>Test: Optional.empty()
    end
    Note over MySQL: MySQL 조회는 처음 한 번
```

### TTL Jitter는 만료 시점을 어떻게 분산하나?

```mermaid
sequenceDiagram
    autonumber

    box rgba(37, 99, 235, 0.40) 테스트 실행
    participant Test as RedisTemplateBookCacheTest
    end

    box rgba(245, 158, 11, 0.40) 앱
    participant Service as RedisTemplateBookCacheService
    end

    box rgba(219, 39, 119, 0.40) 저장소
    participant Redis as Redis
    end

    Test->>Service: cacheBooksWithJitter(books, 60초, 10초)
    loop 책마다
        Service->>Service: key hash로 jitter 계산
        Service->>Redis: SET book:id JSON EX 60~70초
    end
    Test->>Redis: TTL book:1 ... book:5 조회
    Redis-->>Test: 서로 다른 TTL
```

### `RedisHotKeyLockTest`에서 락이 없으면 어떻게 되나?

```mermaid
sequenceDiagram
    autonumber

    box rgba(37, 99, 235, 0.40) 테스트 실행
    participant Test as RedisHotKeyLockTest
    end

    box rgba(22, 163, 74, 0.40) 동시 요청
    participant T1 as 요청 1
    participant T2 as 요청 2
    end

    box rgba(245, 158, 11, 0.40) 앱
    participant Service as RedisHotKeyBookCacheService
    participant Origin as BookOriginService
    participant Repository as BookRepository
    end

    box rgba(219, 39, 119, 0.40) 저장소
    participant Redis as Redis
    participant MySQL as MySQL
    end

    T1->>Test: ready.countDown()
    T2->>Test: ready.countDown()
    Test->>Test: 40개 요청이 ready에 도달할 때까지 대기
    Test->>Test: start.countDown()으로 모든 worker 해제
    par 요청 1
        T1->>Service: findWithoutLock(1L)
        Service->>Redis: GET hot-book:1
        Redis-->>Service: miss
        Service->>Origin: findById(1L)
        Origin->>Repository: findById(1L)
        Repository->>MySQL: SELECT books
        MySQL-->>Repository: BookEntity
        Repository-->>Origin: BookEntity
        Origin-->>Service: Book
        Service->>Redis: SET hot-book:1 JSON EX 10분
    and 요청 2
        T2->>Service: findWithoutLock(1L)
        Service->>Redis: GET hot-book:1
        Redis-->>Service: miss
        Service->>Origin: findById(1L)
        Origin->>Repository: findById(1L)
        Repository->>MySQL: SELECT books
        MySQL-->>Repository: BookEntity
        Repository-->>Origin: BookEntity
        Origin-->>Service: Book
        Service->>Redis: SET hot-book:1 JSON EX 10분
    end
    Note over MySQL: 동시에 miss가 나면 원본 저장소 조회가 여러 번 발생
```

### Redis 분산 락과 Double-Checked Locking은 어떻게 줄이나?

```mermaid
sequenceDiagram
    autonumber

    box rgba(37, 99, 235, 0.40) 테스트 실행
    participant Test as RedisHotKeyLockTest
    end

    box rgba(22, 163, 74, 0.40) 동시 요청
    participant T1 as 요청 1
    participant T2 as 요청 2
    end

    box rgba(245, 158, 11, 0.40) 앱
    participant Service as RedisHotKeyBookCacheService
    participant Origin as BookOriginService
    participant Repository as BookRepository
    end

    box rgba(219, 39, 119, 0.40) 저장소
    participant Redis as Redis
    participant MySQL as MySQL
    end

    T1->>Test: ready.countDown()
    T2->>Test: ready.countDown()
    Test->>Test: 40개 요청이 ready에 도달할 때까지 대기
    Test->>Test: start.countDown()으로 모든 worker 해제
    T1->>Service: findWithDistributedLock(1L)
    Service->>Redis: GET hot-book:1
    Redis-->>Service: miss
    Service->>Redis: SET hot-book:1:lock token NX EX 3
    Redis-->>Service: T1만 lock 획득
    T2->>Service: findWithDistributedLock(1L)
    Service->>Redis: GET hot-book:1
    Redis-->>Service: miss
    Service->>Redis: SET hot-book:1:lock token NX EX 3
    Redis-->>Service: T2 lock 획득 실패
    Service->>Redis: T1: GET hot-book:1 재확인
    Redis-->>Service: miss
    Service->>Origin: T1: findById(1L)
    Origin->>Repository: findById(1L)
    Repository->>MySQL: SELECT books
    MySQL-->>Repository: BookEntity
    Repository-->>Origin: BookEntity
    Origin-->>Service: Book
    Service->>Redis: T1: SET hot-book:1 JSON EX 10분
    Service->>Redis: T1: Lua로 lock 해제
    Service-->>T1: Book

    Note over T2,Redis: T2는 lock 획득 실패 후 캐시 채움 대기
    T2->>Service: wait loop
    Service->>Redis: GET hot-book:1
    Redis-->>Service: JSON
    Service-->>T2: Book
    Note over MySQL: 원본 저장소 조회는 한 번
```

#### `findWithDistributedLock()` 내부에서는 어떻게 동작하나?

이 메서드는 모든 요청에 락을 거는 것이 아니라, 먼저 Redis 캐시를 조회하고 **캐시가 없을 때만** 락 획득을 시도한다.

1. `hot-book:{id}`를 조회한다. 값이 있으면 JSON을 `Book`으로 역직렬화해 바로 반환한다.
2. 캐시가 없으면 `hot-book:{id}:lock`을 락 키로 사용하고, 현재 요청만의 UUID 토큰을 만든다.
3. Redis의 `SET NX` 성격을 가진 `setIfAbsent()`로 3초짜리 락을 원자적으로 획득한다.
4. 락을 얻은 요청은 캐시를 한 번 더 확인한다. 첫 조회와 락 획득 사이에 다른 요청이 캐시를 채웠을 수 있기 때문이다.
5. 두 번째 조회도 실패했을 때만 MySQL을 조회하고, 책이 있으면 Redis에 10분 동안 저장한다.
6. 반환하기 전 `finally`에서 Lua 스크립트를 실행한다. Redis에 저장된 토큰이 내 토큰과 같을 때만 락을 삭제한다.
7. 락을 얻지 못한 요청은 MySQL을 직접 조회하지 않고, 10ms 간격으로 최대 100회 캐시가 채워졌는지 확인한다.

```mermaid
sequenceDiagram
    autonumber

    box rgba(22, 163, 74, 0.40) 요청
    participant Caller as 호출 요청
    end

    box rgba(245, 158, 11, 0.40) 앱
    participant Service as RedisHotKeyBookCacheService
    participant Origin as BookOriginService
    participant Repository as BookRepository
    end

    box rgba(219, 39, 119, 0.40) 저장소
    participant Redis as Redis
    participant MySQL as MySQL
    end

    Caller->>Service: findWithDistributedLock(id)
    Service->>Redis: GET hot-book:{id}

    alt 첫 번째 캐시 조회 hit
        Redis-->>Service: Book JSON
        Service-->>Caller: Book
    else 첫 번째 캐시 조회 miss
        Redis-->>Service: null
        Service->>Service: UUID token 생성
        Service->>Redis: SET lockKey token NX (TTL 3초)

        alt 락 획득 성공
            Redis-->>Service: true
            Service->>Redis: GET hot-book:{id} (Double Check)

            alt 두 번째 캐시 조회 hit
                Redis-->>Service: Book JSON
                Note over Service: 반환할 Book 준비
            else 두 번째 캐시 조회 miss
                Redis-->>Service: null
                Service->>Origin: findById(id)
                Origin->>Repository: findById(id)
                Repository->>MySQL: SELECT books
                MySQL-->>Repository: BookEntity 또는 조회 결과 없음
                Repository-->>Origin: Optional<BookEntity>
                Origin-->>Service: Optional<Book>

                opt Book이 존재함
                    Service->>Redis: SET hot-book:{id} JSON (TTL 10분)
                end
                Note over Service: 반환할 원본 조회 결과 준비
            end

            Note over Service: return 전에 finally 실행
            Service->>Redis: Lua로 token 비교 후 lock 삭제
            Redis-->>Service: 삭제 결과
            Service-->>Caller: 준비한 결과 반환
        else 락 획득 실패
            Redis-->>Service: false

            loop 최대 100회 (10ms 간격)
                Service->>Service: 10ms 대기
                Service->>Redis: GET hot-book:{id}
                Redis-->>Service: Book JSON 또는 null
            end

            alt 반복 대기 중 캐시가 채워짐
                Service-->>Caller: Book
            else 100회 모두 cache miss
                Service-->>Caller: Optional.empty()
            end
        end
    end
```

UUID 토큰과 Lua 스크립트가 필요한 이유는 **내가 획득한 락만 해제하기 위해서**다. 예를 들어 작업이 길어져 3초 뒤 기존 락이 만료되고 다른 요청이 새 락을 획득했다면, 먼저 실행된 요청이 단순히 `DEL`을 호출해서 새 락까지 지워서는 안 된다. Lua 스크립트는 토큰 비교와 삭제를 Redis 안에서 한 번에 실행해 이 문제를 막는다.

이 예제에서 락을 얻지 못한 요청은 sleep 시간만 합쳐 약 1초(`10ms × 100회`) 동안 기다리고, 락의 TTL은 3초다. 실제 대기 시간에는 Redis 조회와 스레드 스케줄링 시간도 더해질 수 있다. 원본 조회가 대기 루프보다 오래 걸리면 대기 요청은 캐시가 곧 채워지더라도 먼저 `Optional.empty()`를 받을 수 있다. 또한 존재하지 않는 책은 캐시에 저장하지 않으므로, 같은 ID를 반복 조회하면 원본 저장소를 다시 확인할 수 있다.
