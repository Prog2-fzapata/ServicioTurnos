package ar.edu.um.prog2.fzapata.turnos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class RequiredClaimsValidatorTest {

    private final RequiredClaimsValidator validator = new RequiredClaimsValidator();

    private Jwt jwtWithout(String missingClaim) {
        Map<String, Object> claims = new HashMap<>(Map.of(
                JwtClaims.USER_ID, 42L,
                JwtClaims.AUTHORITIES, "ROLE_USER"));
        claims.remove(missingClaim);
        claims.put("sub", "maria");
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "HS512"), claims);
    }

    @Test
    void tokenWithUidAndAuthoritiesIsAccepted() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithout("otro"));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void tokenWithoutUidIsRejected() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithout(JwtClaims.USER_ID));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(error -> error.getDescription().contains(JwtClaims.USER_ID));
    }

    @Test
    void tokenWithoutAuthoritiesIsRejected() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithout(JwtClaims.AUTHORITIES));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(error -> error.getDescription().contains(JwtClaims.AUTHORITIES));
    }
}
