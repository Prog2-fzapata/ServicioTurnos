package ar.edu.um.prog2.fzapata.turnos.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.um.prog2.fzapata.turnos.TestcontainersConfiguration;
import lombok.RequiredArgsConstructor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class SecurityIntegrationTest {

    private final MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void openApiDocumentIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // Con este esquema Swagger UI muestra el botón «Authorize»
    @Test
    void openApiDeclaresBearerJwt() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0]['bearer-jwt']").exists());
    }

    @Test
    void anythingElseRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/turnos")).andExpect(status().isUnauthorized());
    }
}
