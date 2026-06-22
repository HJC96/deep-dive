# deep-dive

공부하다 직접 돌려보고 싶은 내용을 정리하는 실험실. 주제 하나당 `labs/` 아래 모듈 하나.

## 모듈

- `coupon-issue` — 선착순 쿠폰 발급 동시성 문제. 락 없음 / synchronized / 원자적 UPDATE / 비관적 락 / 낙관적 락 / 네임드 락 비교
- `java-sandbox` — 디자인 패턴, 리플렉션 등 자바 문법 실험용 공간
- `cache-patterns` — Cache Aside / Read Through / Write Through / Write Around / Write Behind 캐시 패턴 비교
- `spring-cache` — Spring Cache를 로컬 인메모리 캐시로 사용할 때의 동작 실험

## 실행

```bash
./gradlew test                # 전체 모듈
./gradlew :coupon-issue:test  # 특정 모듈만
./gradlew :cache-patterns:test # 캐시 패턴 모듈만
./gradlew :spring-cache:test   # Spring Cache 모듈만
```
