package dev.deepdive.sandbox.crypto;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * {@link AesProviderRegistrationSingleThreadBenchmark}와 <strong>완전히 같은 실험</strong>을
 * {@link Threads @Threads(8)}, 즉 8개 스레드가 동시에 두드리는 상황에서 돌린다.
 *
 * <h2>이 벤치마크가 따로 있는 이유 — 스레드 확장성 관찰</h2>
 * "매번 Provider를 등록"하는 {@link AesUtilBefore} 경로는 호출마다 무거운
 * {@code new BouncyCastleProvider()}를 만들고, JVM 전역인 {@code java.security.Security}의
 * Provider 목록에 등록을 <em>시도</em>한다(이 목록 갱신은 동기화로 보호된다). 스레드를 늘렸을 때
 * 두 경로의 처리량이 각각 어떻게 변하는지를 단일 스레드 기준선과 나란히 비교하는 것이 목적이다.
 *
 * <p><b>주의 — 직관과 반대 결과가 나오고, {@code -prof}로 확정했다.</b> "전역 락 쓰는 느린 repeated가
 * 멀티스레드에서 더 손해"라 추측하기 쉽지만 정반대다. 측정상 repeated는 ×6.7로 잘 확장되고
 * (gc 프로파일: op당 ~2.85MB 할당 → 무거운 {@code new BouncyCastleProvider()} 생성이 지배 비용이며
 * 이는 스레드 로컬이라 병렬화된다), 빠른 once가 ×1.4로 거의 확장되지 않는다. stack 프로파일을 보면
 * 8스레드 once는 시간의 51%를 <b>{@code BouncyCastleProvider.getService}(synchronized) 락 대기</b>로
 * 소모한다 — {@code Cipher.getInstance(..., "BC")}가 부르는 이 동기화 메서드에 8스레드가 줄을 서기 때문.
 * 결국 "직렬 구간 비중이 큰 쪽(=일이 적은 once)"이 확장을 못 하는 암달의 법칙 사례다.
 * 자세한 수치·프로파일 결과는 모듈 README의 "프로파일링으로 검증"·"해석" 절 참고.
 *
 * <h2>실행</h2>
 * <pre>{@code
 * ./gradlew :java-sandbox:jmh \
 *     -PjmhIncludes=AesProviderRegistrationEightThreadsBenchmark
 * }</pre>
 *
 * @see AesProviderRegistrationSingleThreadBenchmark 단일 스레드 기준선(baseline). 각 어노테이션의
 *      자세한 의미는 그쪽 주석 참고.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
// 단일 스레드판과의 유일한 차이. 8개 스레드가 같은 @Benchmark 메서드를 동시에 호출한다.
// 이때 ops/s는 8스레드 '합계' 처리량이다. 측정 결과: repeated는 ×6.7로 잘 확장(할당이 병렬화),
// once는 ×1.4로 거의 확장 안 됨(공유 Provider의 synchronized getService에서 직렬화).
@Threads(8)
public class AesProviderRegistrationEightThreadsBenchmark {

    /** "매번 Provider 등록" 경로. 매 호출 무거운 BouncyCastleProvider 생성/할당이 지배 비용이다. */
    @Benchmark
    public void encrypt_withRepeatedProviderRegistration(CryptoInput input, Blackhole blackhole) throws Exception {
        // blackhole.consume: 반환값을 소비해 JIT의 죽은 코드 제거를 막는다(단일 스레드판 참고).
        blackhole.consume(AesUtilBefore.encrypt(input.plainText, input.key, input.iv));
    }

    /** "Provider 1회 등록 후 재사용" 경로. 단일 스레드는 빠르지만, 8스레드에서는
     *  Cipher.getInstance가 부르는 synchronized getService 경합으로 확장이 거의 안 된다(×1.4). */
    @Benchmark
    public void encrypt_withProviderInitializedOnce(CryptoInput input, Blackhole blackhole) throws Exception {
        blackhole.consume(AesUtilAfter.encrypt(input.plainText, input.key, input.iv));
    }

    /**
     * 읽기 전용 입력. {@link Scope#Benchmark}이므로 8개 스레드가 이 인스턴스 하나를 공유한다.
     * key/iv/plainText는 절대 수정하지 않으니 공유해도 스레드 안전하며, 측정 시간에 객체 생성 비용이
     * 섞이지 않도록 루프 밖에서 한 번만 준비한다.
     */
    @State(Scope.Benchmark)
    public static class CryptoInput {

        final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        final byte[] iv = "abcdef9876543210".getBytes(StandardCharsets.UTF_8);
        final byte[] plainText = """
                BouncyCastle provider registration benchmark plain text.
                The AES algorithm, key, iv, and payload are identical for both paths.
                """.getBytes(StandardCharsets.UTF_8);
    }
}
