/**
 * Manejador de éxito tras login OAuth2 con Google.
 * <p>
 * Sincroniza o crea el usuario en MongoDB, genera JWT, lo guarda en cookie,
 * autentica el request actual en Spring Security y redirige al dashboard.
 * </p>
 *
 * @author proyecto-aula
 */
package com.proyecto.aula.playgroundv2.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.proyecto.aula.playgroundv2.model.Usuario;
import com.proyecto.aula.playgroundv2.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Puente entre el flujo OAuth2 de Spring y la autenticación JWT interna de la aplicación.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final UsuarioRepository userRepository;

    /**
     * Inyección de servicios JWT y repositorio de usuarios.
     */
    public OAuth2AuthenticationSuccessHandler(
            JwtService jwtService,
            JwtCookieService jwtCookieService,
            UsuarioRepository userRepository) {
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.userRepository = userRepository;
    }

    /**
     * Callback ejecutado cuando Google autentica correctamente al usuario.
     * <p>
     * Flujo: leer email del {@link OAuth2User} → buscar o crear {@link Usuario} en Mongo →
     * construir {@link User} de Spring → generar JWT → cookie → contexto de seguridad → redirect.
     * </p>
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        // Aquí se obtiene la información que viene de Google
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // Tomamos el email como username
        String email = oAuth2User.getAttribute("email");

        if (email == null || email.isBlank()) {
            throw new RuntimeException("Google no retornó email");
        }

        // --- Sincronización con colección de usuarios ---

        // Buscar o crear usuario en Mongo
        Optional<Usuario> existingUser = userRepository.findByUsername(email)
                .or(() -> userRepository.findByEmail(email));

        Usuario user;
        if (existingUser.isPresent()) { // usuario ya existente
            user = existingUser.get();
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                user.setEmail(email);
                userRepository.save(user);
            }
        } else {
            user = new Usuario(); // usuario nuevo
            user.setUsername(email);
            user.setEmail(email);
            user.setPassword(""); // no aplica para OAuth
            user.setRole("USER");
            userRepository.save(user); // integra OAuth con app interna
        }

        // --- Preparar UserDetails y JWT ---

        String role = user.getRole();
        if (role != null && role.toUpperCase().startsWith("ROLE_")) {
            role = role.substring(5);
        }
        if (role == null || role.isBlank()) {
            role = "USER";
        }

        // Crear UserDetails compatible con el JwtService
        var userDetails = User.withUsername(user.getUsername())
                .password("")
                .roles(role)
                .build();

        // Generar JWT
        String token = jwtService.generateToken(userDetails);

        jwtCookieService.addJwtCookie(response, token); // guarda el JWT en el navegador

        // autenticar en este mismo request (evita 403)
        var authToken = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authToken);

        // Redirección final
        response.sendRedirect("/dashboard");
    }
}
