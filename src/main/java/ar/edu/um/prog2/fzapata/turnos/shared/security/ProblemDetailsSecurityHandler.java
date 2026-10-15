package ar.edu.um.prog2.fzapata.turnos.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import ar.edu.um.prog2.fzapata.turnos.shared.web.ProblemDetailsFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

// Los filtros de seguridad cortan el pedido antes de llegar a los controllers, así que
// @RestControllerAdvice no los ve: el 401 y el 403 se escriben acá con el mismo formato problem+json
@Component
@RequiredArgsConstructor
public class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemDetailsFactory problemDetailsFactory;
    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        // Solo "Bearer", sin error_description: no se revela si el token venció o es inválido
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, problemDetailsFactory.unauthorized(request.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, problemDetailsFactory.forbidden(request.getRequestURI()));
    }

    private void write(HttpServletResponse response, ResponseEntity<ProblemDetail> problem) throws IOException {
        response.setStatus(problem.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), problem.getBody());
    }
}
