package ar.edu.um.prog2.fzapata.turnos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

class JwtConfigurationTest {

    private String base64OfBytes(int length) {
        return Base64.getEncoder().encodeToString(new byte[length]);
    }

    @Test
    void secretOf64BytesIsAccepted() {
        SecretKey key = JwtConfiguration.secretKey(base64OfBytes(64));

        assertThat(key.getEncoded()).hasSize(64);
        assertThat(key.getAlgorithm()).isEqualTo("HS512");
    }

    @Test
    void secretShorterThan512BitsIsRejected() {
        String shortSecret = base64OfBytes(32);

        assertThatThrownBy(() -> JwtConfiguration.secretKey(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("512 bits");
    }

    @Test
    void secretThatIsNotBase64IsRejected() {
        assertThatThrownBy(() -> JwtConfiguration.secretKey("cambiar-esto!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }
}
