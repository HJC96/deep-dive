# spring-cache

Spring Cache를 로컬 인메모리 캐시로 사용할 때의 동작을 관찰하는 실험실.

## 로컬 캐싱

로컬 캐싱은 서버 자신의 JVM 메모리에 데이터를 저장한다.

이 모듈은 원본 데이터 저장소로 MySQL을 사용하고, 외부 Redis 없이 Spring의 `ConcurrentMapCacheManager`를 직접 등록해서 로컬 캐시를 관찰한다. 캐시 저장소는 `ConcurrentHashMap` 기반이므로 네트워크 호출이 없고 빠르지만, 캐시 데이터가 서버 메모리를 사용한다.

잘 맞는 데이터:

- 국가별 공휴일 목록
- 서비스 설정값
- 거의 바뀌지 않는 코드성 데이터

주의할 데이터:

- 사용자별로 값이 다른 데이터
- 서버가 여러 대일 때 서버 간 동기화가 필요한 데이터
- JVM 메모리를 크게 차지할 수 있는 대용량 데이터

## Spring Cache 추상화

비즈니스 코드는 어노테이션으로 캐싱 의도만 드러낸다.

| 어노테이션 | 동작 |
| --- | --- |
| `@Cacheable` | 캐시를 먼저 확인하고, miss면 메서드 실행 후 결과 저장 |
| `@CachePut` | 메서드를 항상 실행하고 결과로 캐시를 덮어씀 |
| `@CacheEvict` | 메서드 실행 후 지정한 캐시 제거 |

내부 흐름:

```text
프록시 -> CacheInterceptor -> CacheManager -> Cache
```

이 모듈에서는 `CacheManager` 구현체로 `ConcurrentMapCacheManager`를 사용한다. 실제 캐시 저장소가 로컬인지 Redis인지는 비즈니스 코드가 아니라 `CacheManager`가 숨긴다.

## 캐시 키

캐시 키가 잘못되면 다른 요청이 같은 값을 공유하거나, 같은 요청인데 매번 miss가 날 수 있다.

기본 키는 Spring의 `SimpleKeyGenerator`가 만든다.

- 파라미터가 없으면 `SimpleKey.EMPTY`
- 파라미터가 하나면 그 파라미터 자체
- 파라미터가 여러 개면 `SimpleKey`

따라서 DTO를 키로 사용할 때는 `equals()`와 `hashCode()`가 중요하다. `BookSearchCondition`은 record라서 같은 값이면 같은 키가 되고, `BookSearchConditionWithoutEquals`는 객체 동일성 기준이라 같은 값이어도 다른 키가 된다.

필요하면 `key = "#id"`처럼 특정 파라미터만 키로 지정할 수 있다. 이 모듈의 `findBookSummary(long id, String language)`는 일부러 `id`만 키로 써서 `language`가 달라도 같은 캐시 값을 반환하는 상황을 보여준다.

로컬 캐시를 쓰는 여러 WAS 환경에서는 한 서버에서 `@CacheEvict`를 실행해도 다른 서버의 로컬 캐시까지 지워지지 않는다. Redis 같은 공용 캐시를 바로 도입할 수 없다면 공통 테이블의 `max(updatedAt)`을 테이블 버전처럼 캐시 키에 포함해, 테이블 안의 어떤 row가 바뀌든 새 버전 키로 자연스럽게 cache miss가 나도록 만들 수 있다. 이 모듈의 `findBookNameByIdWithTableVersion(long id)`은 JPA Repository로 `max(updatedAt)`을 조회하고, `{max(updatedAt), id}`를 키로 사용해 기존 캐시를 지우지 않고도 공통 테이블 전체를 새 캐시 세대로 넘기는 방식을 보여준다.

실제 운영에서는 `max(updated_at)` 조회가 가벼워야 하므로 `updated_at` 인덱스를 함께 둔다. 만약 공통코드 그룹 같은 구분 컬럼이 있는 구조라면 `(group_key, updated_at)` 형태의 복합 인덱스를 고려할 수 있다. 이 샘플의 `books` 테이블도 `idx_books_updated_at` 인덱스를 만든다.

## 실험 목록

`BookLookupServiceCacheTest`에서 확인하는 내용:

- `ConcurrentMapCacheManager`를 직접 빈으로 등록한다.
- `@Cacheable` 첫 조회는 miss라 메서드가 실행된다.
- 두 번째 동일 조회는 hit라 메서드가 다시 실행되지 않는다.
- `@CachePut`은 기존 캐시가 있어도 메서드를 항상 실행하고 결과로 캐시를 덮어쓴다.
- `@CacheEvict`는 메서드 실행 후 지정한 캐시를 제거한다.
- 캐시 이름이 다르면 같은 ID라도 서로 다른 캐시 공간이다.
- `key = "#id"`는 다른 파라미터를 캐시 키에서 제외한다.
- MySQL의 `max(updated_at)`과 `id`를 함께 키로 쓰면 evict 없이 공통 테이블 전체가 새 버전에서 cache miss가 난다.
- record DTO는 `SimpleKeyGenerator`에서 같은 키로 동작한다.
- `equals/hashCode`가 없는 DTO는 논리적으로 같은 조건이어도 다른 키가 된다.

테스트는 Testcontainers MySQL 8.0 컨테이너를 띄워 Spring Data JPA가 실제 MySQL에 질의하는 흐름으로 검증한다.

## 한계

로컬 캐시는 서버 하나의 메모리에 의존한다.

- 서버 재시작 시 캐시가 사라진다.
- 서버가 여러 대면 각 서버가 서로 다른 캐시를 가진다.
- 대용량 캐시는 JVM 메모리에 부담을 준다.
- 서버 간 캐시 동기화가 어렵다.

분산 서버 환경에서는 이 한계 때문에 Redis 같은 리모트 캐시를 고려한다.

## 시작점

- `SpringCacheApplication`: Spring Boot와 캐시 활성화
- `CacheConfig`: `ConcurrentMapCacheManager` 직접 등록
- `CatalogLookupService`: `@Cacheable`을 붙인 최소 서비스
- `BookLookupService`: `@Cacheable`, `@CachePut`, `@CacheEvict`, 캐시 키 실험
- `BookLookupServiceCacheTest`: 로컬 캐시 hit/miss와 키 동작 검증
