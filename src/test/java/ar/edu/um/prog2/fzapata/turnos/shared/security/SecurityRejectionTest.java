package ar.edu.um.prog2.fzapata.turnos.shared.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import ar.edu.um.prog2.fzapata.turnos.shared.security.SecurityRejectionTest.SecurityProbeController;
import ar.edu.um.prog2.fzapata.turnos.shared.web.GlobalExceptionHandler;
import ar.edu.um.prog2.fzapata.turnos.shared.web.ProblemDetailsFactory;
import lombok.RequiredArgsConstructor;

// Turnos todavía no tiene endpoints: un controller de prueba ejercita el 401, el 403 y la lectura del uid
@WebMvcTest(SecurityProbeController.class)
@Import({SecurityProbeController.class, SecurityConfiguration.class, JwtConfiguration.class,
        ProblemDetailsSecurityHandler.class, ProblemDetailsFactory.class, GlobalExceptionHandler.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class SecurityRejectionTest {

    @RestController
    static class SecurityProbeController {

        // El uid sale del token y nunca de la URL ni del cuerpo
        @GetMapping("/api/probe")
        String userId(@AuthenticationPrincipal Jwt jwt) {
            return String.valueOf((Object) jwt.getClaim(JwtClaims.USER_ID));
        }

        @GetMapping("/api/probe/admin")
        @PreAuthorize("hasAuthority('ROLE_ADMIN')")
        String adminOnly() {
            return "ok";
        }
    }

    private final MockMvc mockMvc;
    private final SecretKey jwtSecretKey;

    private String token(SecretKey key, MacAlgorithm algorithm, Instant issuedAt, Instant expiresAt,
            boolean withUid, boolean withAuthorities) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject("maria").issuedAt(issuedAt).expiresAt(expiresAt);
        if (withUid) {
            claims.claim(JwtClaims.USER_ID, 42L);
        }
        if (withAuthorities) {
            claims.claim(JwtClaims.AUTHORITIES, "ROLE_USER");
        }
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims.build()))
                .getTokenValue();
    }

    private String validToken() {
        Instant now = Instant.now();
        return token(jwtSecretKey, MacAlgorithm.HS512, now, now.plus(1, ChronoUnit.HOURS), true, true);
    }

    private ResultActions callWithBearer(String bearer) throws Exception {
        return mockMvc.perform(get("/api/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer));
    }

    // Todos los rechazos del filtro deben verse igual: no se revela por qué falló el token
    private void expectUnauthorizedProblem(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Falta el token de autenticación o no es válido"))
                .andExpect(jsonPath("$.path").value("/api/probe"));
    }

    @Test
    void requestWithoutTokenIsRejectedWithProblemJson() throws Exception {
        expectUnauthorizedProblem(mockMvc.perform(get("/api/probe")));
    }

    @Test
    void malformedTokenIsRejectedWithProblemJson() throws Exception {
        expectUnauthorizedProblem(callWithBearer("esto-no-es-un-jwt"));
    }

    @Test
    void expiredTokenIsRejectedWithProblemJson() throws Exception {
        Instant longAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        expectUnauthorizedProblem(callWithBearer(token(jwtSecretKey, MacAlgorithm.HS512, longAgo,
                longAgo.plus(1, ChronoUnit.HOURS), true, true)));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejectedWithProblemJson() throws Exception {
        SecretKey otherKey = new SecretKeySpec(new byte[64], "HmacSHA512");
        Instant now = Instant.now();

        expectUnauthorizedProblem(callWithBearer(token(otherKey, MacAlgorithm.HS512, now,
                now.plus(1, ChronoUnit.HOURS), true, true)));
    }

    // Con la clave correcta pero otro algoritmo (HS256): el decoder solo acepta HS512
    @Test
    void tokenSignedWithAnotherAlgorithmIsRejectedWithProblemJson() throws Exception {
        Instant now = Instant.now();

        expectUnauthorizedProblem(callWithBearer(token(jwtSecretKey, MacAlgorithm.HS256, now,
                now.plus(1, ChronoUnit.HOURS), true, true)));
    }

    @Test
    void tokenWithTamperedPayloadIsRejectedWithProblemJson() throws Exception {
        String[] parts = validToken().split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"maria\",\"uid\":1,\"auth\":\"ROLE_ADMIN\",\"exp\":9999999999}".getBytes());

        expectUnauthorizedProblem(callWithBearer(parts[0] + "." + forgedPayload + "." + parts[2]));
    }

    @Test
    void tokenWithoutUidIsRejectedWithProblemJson() throws Exception {
        Instant now = Instant.now();

        expectUnauthorizedProblem(callWithBearer(token(jwtSecretKey, MacAlgorithm.HS512, now,
                now.plus(1, ChronoUnit.HOURS), false, true)));
    }

    @Test
    void tokenWithoutAuthoritiesIsRejectedWithProblemJson() throws Exception {
        Instant now = Instant.now();

        expectUnauthorizedProblem(callWithBearer(token(jwtSecretKey, MacAlgorithm.HS512, now,
                now.plus(1, ChronoUnit.HOURS), true, false)));
    }

    @Test
    void validTokenIsAcceptedAndTheUserIdComesFromTheToken() throws Exception {
        callWithBearer(validToken()).andExpect(status().isOk()).andExpect(content().string("42"));
    }

    // Todo lo que no es expresamente público exige token, también las rutas que no existen
    @Test
    void unknownPathWithoutTokenIsRejectedBeforeAnyRouting() throws Exception {
        mockMvc.perform(get("/api/no-existe")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutTheRequiredRoleGetsForbiddenProblemJson() throws Exception {
        mockMvc.perform(get("/api/probe/admin").with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value("No tiene permiso para realizar esta operación"))
                .andExpect(jsonPath("$.path").value("/api/probe/admin"));
    }

    @Test
    void userWithTheRequiredRoleIsAccepted() throws Exception {
        mockMvc.perform(get("/api/probe/admin").with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }
}
