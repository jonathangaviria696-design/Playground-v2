/**
 * Servicio para emitir y borrar la cookie HTTP que transporta el JWT.
 * <p>
 * Centraliza los atributos de seguridad de la cookie ({@code HttpOnly}, {@code SameSite}, etc.)
 * para que login formulario, OAuth2 y logout usen el mismo comportamiento.
 * </p>
 *
 * @author proyecto-aula
 * @see JwtAuthenticationFilter#JWT_COOKIE_NAME
 */
package com.proyecto.aula.playgroundv2.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Escribe cabeceras {@code Set-Cookie} con el token JWT o una cookie vacía al cerrar sesión.
 */
@Service
public class JwtCookieService {

    /**
     * Añade al response una cookie con el JWT recién generado.
     * <p>
     * Cómo funciona: construye un {@link ResponseCookie} con nombre {@link JwtAuthenticationFilter#JWT_COOKIE_NAME},
     * {@code httpOnly} para mitigar XSS, {@code SameSite=Lax}, path {@code /} y maxAge de 24 horas.
     * {@code secure=false} permite desarrollo local por HTTP.
     * </p>
     *
     * @param response respuesta HTTP donde se enviará Set-Cookie
     * @param token    JWT en texto plano (valor de la cookie)
     */
    public void addJwtCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.JWT_COOKIE_NAME, token)
                .httpOnly(true) // evita que JavaScript lea la cookie
                .secure(false) // permite probar en local con HTTP
                .sameSite("Lax") // ayuda a reducir ciertos riesgos CSRF.
                .path("/")
                .maxAge(24 * 60 * 60)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Invalida la cookie JWT en el cliente (maxAge 0, valor vacío).
     * Se invoca típicamente desde el manejador de logout de Spring Security.
     */
    public void clearJwtCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.JWT_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
