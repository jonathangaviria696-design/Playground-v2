package com.proyecto.aula.playgroundv2.controller.api;

import java.util.Map;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.aula.playgroundv2.dto.LoginRequest;
import com.proyecto.aula.playgroundv2.dto.LoginResponse;
import com.proyecto.aula.playgroundv2.dto.RegistroUsuarioForm;
import com.proyecto.aula.playgroundv2.security.JwtService;
import com.proyecto.aula.playgroundv2.service.UsuarioService;

import jakarta.validation.Valid;

/**
 * API REST de autenticación y registro para clientes que consumen JWT.
 * <p>
 * Ruta base: {@code /api/auth}. Todas las rutas son públicas a nivel de filtro HTTP
 * ({@code permitAll}); el endpoint {@code /me} requiere enviar un token Bearer válido
 * para obtener datos del principal autenticado.
 * </p>
 *
 * @see JwtService
 * @see UsuarioService
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioService appUserService;

    public AuthApiController(AuthenticationManager authenticationManager, JwtService jwtService,
            UsuarioService appUserService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.appUserService = appUserService;
    }

    /**
     * Autentica credenciales y devuelve un token JWT.
     * <ul>
     *   <li><b>URL:</b> {@code POST /api/auth/login}</li>
     *   <li><b>Acceso:</b> público</li>
     *   <li><b>Cuerpo:</b> JSON {@code LoginRequest} (username, password)</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con {@code LoginResponse} que incluye el token JWT;
     *       error 401 si las credenciales son inválidas</li>
     * </ul>
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        String token = jwtService.generateToken((UserDetails) authentication.getPrincipal());
        return new LoginResponse(token);
    }

    /**
     * Registra un nuevo usuario en el sistema.
     * <ul>
     *   <li><b>URL:</b> {@code POST /api/auth/registro}</li>
     *   <li><b>Acceso:</b> público</li>
     *   <li><b>Cuerpo:</b> JSON {@code RegistroUsuarioForm} validado</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con {@code {"message": "Usuario registrado correctamente"}};
     *       {@code 400} si las contraseñas no coinciden o hay error de negocio</li>
     * </ul>
     */
    @PostMapping("/registro")
    public Map<String, String> registrar(@Valid @RequestBody RegistroUsuarioForm form) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Las contraseñas no coinciden");
        }
        appUserService.registrarUsuario(form);
        return Map.of("message", "Usuario registrado correctamente");
    }

    /**
     * Devuelve el nombre de usuario y los roles del principal autenticado por JWT.
     * <ul>
     *   <li><b>URL:</b> {@code GET /api/auth/me}</li>
     *   <li><b>Acceso:</b> requiere cabecera {@code Authorization: Bearer &lt;token&gt;}</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con mapa {@code username} y {@code roles};
     *       sin token válido, Spring Security responde no autorizado</li>
     * </ul>
     */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of(
                "username", authentication.getName(),
                "roles", authentication.getAuthorities());
    }
}
