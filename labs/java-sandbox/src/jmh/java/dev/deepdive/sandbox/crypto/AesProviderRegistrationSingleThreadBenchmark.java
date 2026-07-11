package dev.deepdive.sandbox.crypto;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * AES 암호화 시 BouncyCastle Provider를 <strong>매번 등록</strong>하는 방식({@link AesUtilBefore})과
 * <strong>한 번만 등록</strong>해 두고 재사용하는 방식({@link AesUtilAfter})의 처리량을
 * <em>단일 스레드</em>에서 비교한다.
 *
 * <h2>왜 직접 재지 않고 JMH를 쓰나</h2>
 * 직접 {@code System.nanoTime()}으로 루프를 돌려 재면 JVM이 측정을 망친다.
 * <ul>
 *   <li><b>JIT 워밍업</b>: 처음 수천 번은 인터프리터로 느리게 돌다가 어느 순간 네이티브로
 *       컴파일되며 빨라진다. 워밍업을 빼면 "느린 초반"이 평균에 섞여 결과가 왜곡된다.</li>
 *   <li><b>죽은 코드 제거(DCE)</b>: 반환값을 아무도 안 쓰면 JIT가 "이 계산은 의미 없다"며
 *       호출 자체를 통째로 지워 버린다. 그러면 "0ns"라는 거짓 결과가 나온다.</li>
 *   <li><b>상수 폴딩</b>: 입력이 컴파일 타임 상수면 결과를 미리 계산해 박아 버린다.</li>
 * </ul>
 * JMH는 워밍업/측정 분리, {@link Blackhole}, {@link State}로 이 세 함정을 모두 막아 준다.
 *
 * <h2>실행</h2>
 * <pre>{@code
 * ./gradlew :labs:java-sandbox:jmh \
 *     -PjmhIncludes=AesProviderRegistrationSingleThreadBenchmark
 * }</pre>
 *
 * <h2>결과 읽는 법</h2>
 * {@link Mode#Throughput} + {@link TimeUnit#SECONDS}이므로 <b>초당 처리 횟수(ops/s, 높을수록 빠름)</b>로
 * 출력된다. {@code withProviderInitializedOnce}의 Score가 {@code withRepeatedProviderRegistration}보다
 * 높으면 "Provider 1회 등록"이 더 빠른 것이다. Error는 ±95% 신뢰구간.
 *
 * @see AesProviderRegistrationEightThreadsBenchmark 동일 실험을 8스레드로 돌려 락 경합 영향을 본다
 */
// @BenchmarkMode: 무엇을 측정할지. Throughput = 단위 시간당 처리 횟수(ops/s). 반대로 AverageTime을
//                 쓰면 1회당 걸린 시간을 잰다. 처리량 비교엔 Throughput이 직관적이다.
@BenchmarkMode(Mode.Throughput)
// @OutputTimeUnit: Score를 어떤 시간 단위로 환산해 출력할지. 초당(ops/s)으로 본다.
@OutputTimeUnit(TimeUnit.SECONDS)
// @Warmup: 본 측정 '전에' 버리는 예열 라운드. JIT 컴파일이 끝나 성능이 안정될 때까지 돌린다.
//          여기 결과는 집계에 포함되지 않는다. (3라운드 × 각 1초)
@Warmup(iterations = 3, time = 1)
// @Measurement: 실제로 집계에 들어가는 측정 라운드. (5라운드 × 각 1초)
@Measurement(iterations = 5, time = 1)
// @Fork: 측정마다 새 JVM 프로세스를 몇 번 띄울지. 앞선 실행이 남긴 JIT 프로파일 같은 상태가
//        결과를 오염시키지 않도록 격리한다. 1이면 깨끗한 JVM에서 1번 측정.
@Fork(1)
// @Threads: 벤치마크 메서드를 동시에 호출하는 스레드 수. 여기서는 1 → 경합 없는 순수 처리량.
@Threads(1)
public class AesProviderRegistrationSingleThreadBenchmark {

    /**
     * "매번 Provider 등록" 방식의 처리량을 잰다. JMH가 이 메서드를 워밍업/측정 동안 반복 호출하며,
     * 1초 안에 몇 번 도는지로 ops/s를 계산한다.
     *
     * @param input     측정 시간에 영향 주지 않도록 미리 준비된 입력(@State)
     * @param blackhole 반환값을 '소비'시켜 JIT의 죽은 코드 제거를 막는 장치
     */
    @Benchmark
    public void encrypt_withRepeatedProviderRegistration(CryptoInput input, Blackhole blackhole) throws Exception {
        // 결과를 그냥 버리면 JIT가 encrypt() 호출을 지워 버린다.
        // blackhole.consume()으로 "이 값은 실제로 쓰인다"고 속여 호출이 살아 있게 한다.
        blackhole.consume(AesUtilBefore.encrypt(input.plainText, input.key, input.iv));
    }

    /**
     * "Provider 1회 등록 후 재사용" 방식의 처리량을 잰다. 위 메서드와 입력·로직이 모두 같고
     * 오직 Provider 등록 횟수만 다르므로, 두 Score 차이가 곧 등록 비용의 차이다.
     */
    @Benchmark
    public void encrypt_withProviderInitializedOnce(CryptoInput input, Blackhole blackhole) throws Exception {
        blackhole.consume(input.aesUtilAfter.encrypt(input.plainText, input.key, input.iv));
    }

    /**
     * 벤치마크에 넣을 입력값을 담는 상태 객체.
     *
     * <p>{@link Scope#Benchmark}는 모든 스레드가 이 인스턴스 하나를 공유한다는 뜻이다. key/iv/plainText는
     * 읽기 전용이라 공유해도 안전하고, 객체 생성 비용이 측정 루프 '밖'에서 한 번만 들도록 빼 두는 게 핵심이다.
     * 만약 이 필드 준비를 @Benchmark 메서드 안에서 했다면 그 시간까지 측정에 섞여 버린다.
     */
    @State(Scope.Benchmark)
    public static class CryptoInput {

        final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        final byte[] iv = "abcdef9876543210".getBytes(StandardCharsets.UTF_8);
        final byte[] plainText = """
                BouncyCastle provider registration benchmark plain text.
                The AES algorithm, key, iv, and payload are identical for both paths.
                """.getBytes(StandardCharsets.UTF_8);
        AesUtilAfter aesUtilAfter;

        @Setup
        public void setUp() {
            Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (provider == null) {
                provider = new BouncyCastleProvider();
                Security.addProvider(provider);
            }
            aesUtilAfter = new AesUtilAfter(provider);
        }
    }
}
