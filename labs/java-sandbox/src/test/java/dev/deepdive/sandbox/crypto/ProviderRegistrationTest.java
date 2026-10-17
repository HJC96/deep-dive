package dev.deepdive.sandbox.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import javax.crypto.Cipher;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

/**
 * {@link AesUtilBefore}가 호출마다 만드는 {@code new BouncyCastleProvider()}가 실제 암호화에 쓰이는지 확인한다.
 *
 * <p>{@code Security.addProvider}는 같은 이름("BC")의 Provider가 이미 있으면 아무것도 추가하지 않고 -1을
 * 돌려준다. {@code Cipher.getInstance(T, "BC")}는 이름으로 등록된 Provider를 찾는다. 그래서 첫 호출 뒤로는
 * 새로 만든 인스턴스가 등록되지도, 쓰이지도 않고 바로 버려진다.
 *
 * <p>Provider 목록은 JVM 전역 상태다. 다른 테스트가 먼저 BC를 등록해 두었을 수도 있으니 "처음 등록"에는
 * 기대지 않고, {@code AesUtilBefore}를 한 번 부른 뒤의 상태를 기준으로 본다.
 */
class ProviderRegistrationTest {

    private static final String TRANSFORMATION = "AES/CBC/PKCS7Padding";

    private final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private final byte[] iv = "abcdef9876543210".getBytes(StandardCharsets.UTF_8);
    private final byte[] plainText = "provider identity".getBytes(StandardCharsets.UTF_8);

    @Test
    void 이미_BC가_등록돼_있으면_addProvider는_새_인스턴스를_추가하지_않고_마이너스1을_돌려준다() throws Exception {
        AesUtilBefore.encrypt(plainText, key, iv);
        Provider registered = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);

        Provider fresh = new BouncyCastleProvider();

        assertThat(Security.addProvider(fresh)).isEqualTo(-1);
        assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isSameAs(registered).isNotSameAs(fresh);
    }

    @Test
    void AesUtilBefore를_여러_번_불러도_등록된_BC는_처음_그_인스턴스_하나뿐이다() throws Exception {
        AesUtilBefore.encrypt(plainText, key, iv);
        Provider registered = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);

        for (int i = 0; i < 5; i++) {
            AesUtilBefore.encrypt(plainText, key, iv);
        }

        assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isSameAs(registered);
        assertThat(Arrays.stream(Security.getProviders())
                .filter(provider -> BouncyCastleProvider.PROVIDER_NAME.equals(provider.getName())))
                .hasSize(1);
    }

    @Test
    void 이름_BC로_만든_Cipher는_방금_만든_Provider가_아니라_등록된_인스턴스를_쓴다() throws Exception {
        AesUtilBefore.encrypt(plainText, key, iv);
        Provider registered = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);

        // AesUtilBefore.encrypt 안의 두 줄을 그대로 재현한다.
        Provider createdInThisCall = new BouncyCastleProvider();
        Security.addProvider(createdInThisCall);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);

        // 암호화는 공유된 등록 인스턴스가 한다. 호출마다 만든 Provider는 할당 비용만 남기고 버려진다.
        assertThat(cipher.getProvider()).isSameAs(registered).isNotSameAs(createdInThisCall);
    }

    @Test
    void AesUtilAfter는_주입받은_Provider_객체를_그대로_쓴다() throws Exception {
        Provider injected = new BouncyCastleProvider();

        // AesUtilAfter.encrypt와 같은 방식: 이름이 아니라 Provider 객체로 찾는다. 등록 여부와 무관하다.
        Cipher cipher = Cipher.getInstance(TRANSFORMATION, injected);

        assertThat(cipher.getProvider()).isSameAs(injected);
        assertThat(new AesUtilAfter(injected).encrypt(plainText, key, iv))
                .isEqualTo(AesUtilBefore.encrypt(plainText, key, iv));
    }
}
