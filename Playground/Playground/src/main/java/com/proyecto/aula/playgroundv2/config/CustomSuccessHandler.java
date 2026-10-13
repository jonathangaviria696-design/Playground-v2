/**
 * Manejador de éxito tras login por formulario (usuario y contraseña).
 * <p>
 * Emite JWT en cookie, respeta la URL guardada antes del login si existe,
 * o redirige a {@code /admin} o {@code /dashboard} según el rol del usuario.
 * </p>
 *
 * @author proyecto-aula
 */
package com.proyecto.aula.playgroundv2.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import com.proyecto.aula.playgroundv2.security.JwtCookieService;
import com.proyecto.aula.playgroundv2.security.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Post-login para credenciales locales: cookie JWT + redirección inteligente.
 */
@Component
public class CustomSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;

    /** Recupera la URL que el usuario intentaba visitar antes de ser enviado a /login. */
    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

    public CustomSuccessHandler(JwtService jwtService, JwtCookieService jwtCookieService) {
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
    }

    /**
     * Se invoca cuando Spring Security valida correctamente usuario/contraseña.
     * <p>
     * Orden: (1) generar JWT y cookie si el principal es {@link UserDetails};
     * (2) si hay {@link SavedRequest}, redirigir allí; (3) si no, admin → /admin, resto → /dashboard.
     * </p>
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            String token = jwtService.generateToken(userDetails);
            jwtCookieService.addJwtCookie(response, token);
        }

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            response.sendRedirect(savedRequest.getRedirectUrl());
            return;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(String::toUpperCase)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ADMIN"));

        response.sendRedirect(isAdmin ? "/admin" : "/dashboard");
    }
}
