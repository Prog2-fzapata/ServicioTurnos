package ar.edu.um.prog2.fzapata.turnos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtPropagationInterceptorTest {

    private final JwtPropagationInterceptor interceptor = new JwtPropagationInterceptor();
    private final MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
            URI.create("http://catalogo.test/api/professionals/15"));

    @AfterEach
    void logOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addsTheTokenOfTheAuthenticatedUserAsBearer() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token-del-usuario").header("alg", "HS512").claim("uid", "42").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        interceptor.intercept(request, new byte[0], (req, body) -> {
            assertThat(req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-del-usuario");
            return new MockClientHttpResponse(new byte[0], 200);
        });
    }

    @Test
    void failsWithoutAuthentication() {
        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], (req, body) -> null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenTheAuthenticationIsNotAJwt() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alguien", "x"));

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], (req, body) -> null))
                .isInstanceOf(IllegalStateException.class);
    }
}
