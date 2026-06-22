# spring-cache

Spring Cache를 로컬 인메모리 캐시로 사용할 때의 동작을 관찰하는 실험실.

## 시작점

- `SpringCacheApplication`: Spring Boot와 캐시 활성화
- `CatalogLookupService`: `@Cacheable`을 붙일 최소 서비스
- `application.yml`: Caffeine 기반 로컬 캐시 설정
