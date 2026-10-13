package ar.edu.um.prog2.fzapata.turnos.shared.security;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

// Un token bien firmado pero sin uid o sin autoridades no puede venir de catálogo:
// se rechaza acá y no más adelante con un error 500
public class RequiredClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final List<String> REQUIRED = List.of(JwtClaims.USER_ID, JwtClaims.AUTHORITIES);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        for (String claim : REQUIRED) {
            if (jwt.getClaim(claim) == null) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Falta el claim " + claim, null));
            }
        }
        return OAuth2TokenValidatorResult.success();
    }
}
