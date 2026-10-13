/**
 * Filtro servlet que autentica peticiones mediante JWT en cabecera o cookie.
 * <p>
 * Se ejecuta una vez por request ({@link org.springframework.web.filter.OncePerRequestFilter})
 * antes del filtro de login por formulario. Si encuentra un token válido, rellena el
 * {@link org.springframework.security.core.context.SecurityContextHolder} sin crear sesión HTTP obligatoria.
 * </p>
 *
 * @author proyecto-aula
 */
package com.proyecto.aula.playgroundv2.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro de autenticación JWT: extrae token, valida y establece el principal en el contexto.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Nombre de la cookie donde el navegador guarda el JWT tras login u OAuth. */
    public static final String JWT_COOKIE_NAME = "JWT_TOKEN";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * Dependencias para validar el token y cargar autoridades desde BD.
     */
    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Punto de entrada del filtro: intenta autenticación JWT y continúa la cadena de filtros.
     * <p>
     * Solo actúa si aún no hay autenticación en el contexto (evita sobrescribir sesión activa).
     * Ante cualquier error de parseo/validación, limpia el contexto y deja pasar la petición
     * (el usuario quedará anónimo hasta otro mecanismo de auth).
     * </p>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        // Luego se Valida el token.
        // Si es válido, autenticar al usuario dentro de Spring Security.

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String username = jwtService.extractUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Obtiene el JWT de la petición: primero cabecera {@code Authorization: Bearer ...},
     * si no, recorre las cookies buscando {@link #JWT_COOKIE_NAME}.
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        if (request.getCookies() == null) {
            return null;
        }
        // Si no llega en header, revisar si existe una cookie JWT_TOKEN.
        for (Cookie cookie : request.getCookies()) {
            if (JWT_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
