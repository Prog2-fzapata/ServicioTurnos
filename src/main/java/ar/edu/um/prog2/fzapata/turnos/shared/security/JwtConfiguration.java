package ar.edu.um.prog2.fzapata.turnos.shared.security;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

// Turnos solo valida tokens: no hay encoder, porque quien los firma es catálogo
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {

    // HS512 es el algoritmo con que firma catálogo; el decoder solo acepta este, así que rechaza tokens firmados con otro
    public static final MacAlgorithm ALGORITHM = MacAlgorithm.HS512;
    // HS512 exige una clave de al menos 512 bits
    static final int MIN_SECRET_BYTES = 64;

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        return secretKey(properties.secret());
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(ALGORITHM).build();
        // Los validadores por defecto controlan vigencia (exp, nbf); se suman los claims obligatorios
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), new RequiredClaimsValidator()));
        return decoder;
    }

    // Falla al arrancar, con un mensaje claro, en vez de aceptar tokens con una clave débil
    static SecretKey secretKey(String base64Secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET no es Base64 válido; debe ser el mismo valor que usa catálogo", e);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos " + MIN_SECRET_BYTES * 8
                    + " bits (" + MIN_SECRET_BYTES + " bytes); debe ser el mismo valor que usa catálogo");
        }
        return new SecretKeySpec(bytes, ALGORITHM.getName());
    }
}
