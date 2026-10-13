/**
 * Servicio de tokens JWT (JSON Web Token) para autenticación stateless.
 * <p>
 * Genera tokens firmados con HMAC a partir de {@link org.springframework.security.core.userdetails.UserDetails},
 * extrae el nombre de usuario (subject) y valida expiración y coincidencia con el usuario cargado.
 * La clave secreta y la duración se configuran en {@code application.properties}
 * ({@code app.jwt.secret}, {@code app.jwt.expiration-ms}).
 * </p>
 *
 * @author proyecto-aula
 */
package com.proyecto.aula.playgroundv2.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Utilidades de creación, parseo y validación de JWT con la librería jjwt.
 */
@Service
public class JwtService {

    /** Clave simétrica derivada del secreto configurado en properties. */
    private final SecretKey secretKey;

    /** Tiempo de vida del token en milisegundos. */
    private final long expirationMillis;

    /**
     * Inicializa clave HMAC y expiración desde configuración externa.
     *
     * @param secret            cadena secreta (debe tener longitud adecuada para HMAC)
     * @param expirationMillis  validez del token en ms
     */
    public JwtService(@Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMillis) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    /**
     * Genera un JWT compacto con subject = username y claim {@code roles}.
     * <p>
     * Cómo funciona: recoge las autoridades del {@link UserDetails}, fija {@code issuedAt}
     * y {@code expiration}, firma con {@link #secretKey} y devuelve el string Bearer-ready.
     * </p>
     */
    public String generateToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extrae el nombre de usuario almacenado en el subject del token.
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Comprueba que el token pertenezca al usuario y no esté caducado.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /** Indica si la fecha de expiración del JWT ya pasó. */
    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    /**
     * Parsea y verifica la firma del token, devolviendo todos los claims.
     * Lanza excepción si el token es inválido o está manipulado.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
