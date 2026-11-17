package ar.edu.um.prog2.fzapata.turnos.shared.security;

// Claims propios del JWT que emite catálogo (deben coincidir con los suyos); turnos solo los lee
public final class JwtClaims {

    public static final String USER_ID = "uid";
    // Autoridades separadas por espacio, como el JWT de JHipster
    public static final String AUTHORITIES = "auth";

    private JwtClaims() {
    }
}
