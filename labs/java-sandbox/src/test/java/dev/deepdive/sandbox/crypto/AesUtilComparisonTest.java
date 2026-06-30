package dev.deepdive.sandbox.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AesUtilComparisonTest {

    @Test
    void 같은_입력에서는_provider_등록_방식과_무관하게_같은_암호문을_만든다() throws Exception {
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        byte[] iv = "abcdef9876543210".getBytes(StandardCharsets.UTF_8);
        byte[] plainText = "same aes input for provider registration comparison".getBytes(StandardCharsets.UTF_8);

        byte[] before = AesUtilBefore.encrypt(plainText, key, iv);
        byte[] after = AesUtilAfter.encrypt(plainText, key, iv);

        assertThat(after).isEqualTo(before);
    }
}
