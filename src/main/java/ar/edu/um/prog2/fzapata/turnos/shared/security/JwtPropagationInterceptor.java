package ar.edu.um.prog2.fzapata.turnos.shared.security;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

// Reenvía a otro servicio el JWT del usuario que hizo el pedido: catálogo lo valida con el mismo secreto
@Component
public class JwtPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Todo pedido a turnos exige JWT, así que no tener usuario acá es un error de programación, no de uso
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new IllegalStateException("No hay un usuario autenticado con JWT para reenviar a otro servicio");
        }
        request.getHeaders().setBearerAuth(jwtAuthentication.getToken().getTokenValue());
        return execution.execute(request, body);
    }
}
