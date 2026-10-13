package com.proyecto.aula.playgroundv2.controller.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.proyecto.aula.playgroundv2.model.Usuario;
import com.proyecto.aula.playgroundv2.service.UsuarioService;

/**
 * API REST de gestión de perfiles de usuario.
 * <p>
 * Ruta base: {@code /api/usuarios}. Requiere autenticación en todas las rutas
 * ({@code authenticated} en seguridad HTTP). Los permisos finos se aplican con
 * {@link PreAuthorize}: lectura/edición del propio perfil, o acciones de administrador
 * sobre cualquier usuario.
 * </p>
 *
 * @see UsuarioService
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioApiController {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;

    public UsuarioApiController(UsuarioService usuarioService, PasswordEncoder passwordEncoder) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Obtiene el perfil del usuario que envía la petición (desde el JWT o sesión).
     * <ul>
     *   <li><b>URL:</b> {@code GET /api/usuarios/me}</li>
     *   <li><b>Acceso:</b> cualquier usuario autenticado ({@code isAuthenticated()})</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con id, username, email, telefono, direccion, role;
     *       {@code 400} con {@code error} si el usuario no existe en BD</li>
     * </ul>
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> obtenerUsuarioActual(Authentication authentication) {
        try {
            Usuario usuario = usuarioService.obtenerPorUsername(authentication.getName());
            return ResponseEntity.ok(Map.of(
                    "id", usuario.getId(),
                    "username", usuario.getUsername(),
                    "email", usuario.getEmail(),
                    "telefono", usuario.getTelefono() != null ? usuario.getTelefono() : "",
                    "direccion", usuario.getDireccion() != null ? usuario.getDireccion() : "",
                    "role", usuario.getRole()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Usuario no encontrado"));
        }
    }

    /**
     * Obtiene el perfil de un usuario por id.
     * <ul>
     *   <li><b>URL:</b> {@code GET /api/usuarios/{id}}</li>
     *   <li><b>Acceso:</b> el propio usuario o rol {@code ADMIN}</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con datos del perfil; {@code 403 Forbidden} sin permiso;
     *       {@code 404 Not Found} si el id no existe</li>
     * </ul>
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> obtenerUsuario(
            @PathVariable String id,
            Authentication authentication) {
        try {
            Usuario usuarioActual = usuarioService.obtenerPorUsername(authentication.getName());
            
            // Solo puede ver su propio perfil o si es ADMIN
            boolean esAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            
            if (!usuarioActual.getId().equals(id) && !esAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No tienes permiso para ver este usuario"));
            }

            Usuario usuario = usuarioService.obtenerPorId(id);
            return ResponseEntity.ok(Map.of(
                    "id", usuario.getId(),
                    "username", usuario.getUsername(),
                    "email", usuario.getEmail(),
                    "telefono", usuario.getTelefono() != null ? usuario.getTelefono() : "",
                    "direccion", usuario.getDireccion() != null ? usuario.getDireccion() : "",
                    "role", usuario.getRole()
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Lista todos los usuarios registrados (vista resumida).
     * <ul>
     *   <li><b>URL:</b> {@code GET /api/usuarios}</li>
     *   <li><b>Acceso:</b> rol {@code ADMIN}</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con lista de mapas (id, username, email, role);
     *       {@code 400} si falla la consulta</li>
     * </ul>
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listarUsuarios() {
        try {
            List<Map<String, Object>> usuarios = usuarioService.obtenerTodos().stream()
                    .map(u -> Map.of(
                            "id", (Object) u.getId(),
                            "username", (Object) u.getUsername(),
                            "email", (Object) u.getEmail(),
                            "role", (Object) u.getRole()
                    ))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(usuarios);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Actualiza campos del perfil de un usuario.
     * <ul>
     *   <li><b>URL:</b> {@code PUT /api/usuarios/{id}}</li>
     *   <li><b>Acceso:</b> el propio usuario o {@code ADMIN}; solo ADMIN puede cambiar {@code role}</li>
     *   <li><b>Cuerpo:</b> JSON con email, telefono, direccion y opcionalmente role (admin)</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con perfil actualizado y mensaje; {@code 403}/{@code 400} en error</li>
     * </ul>
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> actualizarUsuario(
            @PathVariable String id,
            @RequestBody Map<String, String> datosActualizados,
            Authentication authentication) {
        try {
            Usuario usuarioActual = usuarioService.obtenerPorUsername(authentication.getName());
            
            // Solo puede actualizar su propio perfil o si es ADMIN
            boolean esAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            
            if (!usuarioActual.getId().equals(id) && !esAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No tienes permiso para actualizar este usuario"));
            }

            Usuario usuario = usuarioService.obtenerPorId(id);
            
            // Actualizar campos permitidos
            if (datosActualizados.containsKey("email") && datosActualizados.get("email") != null) {
                usuario.setEmail(datosActualizados.get("email"));
            }
            if (datosActualizados.containsKey("telefono") && datosActualizados.get("telefono") != null) {
                usuario.setTelefono(datosActualizados.get("telefono"));
            }
            if (datosActualizados.containsKey("direccion") && datosActualizados.get("direccion") != null) {
                usuario.setDireccion(datosActualizados.get("direccion"));
            }

            // Solo ADMIN puede cambiar el rol
            if (esAdmin && datosActualizados.containsKey("role")) {
                usuario.setRole(datosActualizados.get("role"));
            }

            usuarioService.guardarUsuario(usuario);
            
            return ResponseEntity.ok(Map.of(
                    "id", usuario.getId(),
                    "username", usuario.getUsername(),
                    "email", usuario.getEmail(),
                    "telefono", usuario.getTelefono() != null ? usuario.getTelefono() : "",
                    "direccion", usuario.getDireccion() != null ? usuario.getDireccion() : "",
                    "role", usuario.getRole(),
                    "mensaje", "Usuario actualizado correctamente"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cambia la contraseña del usuario autenticado (solo la propia).
     * <ul>
     *   <li><b>URL:</b> {@code POST /api/usuarios/{id}/cambiar-contrasena}</li>
     *   <li><b>Acceso:</b> solo el usuario cuyo {@code id} coincide con el autenticado</li>
     *   <li><b>Cuerpo:</b> {@code contraseniaActual}, {@code contrasenianueva}, {@code confirmarContrasenia}</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con mensaje; {@code 403}/{@code 400} si no hay permiso o validación falla</li>
     * </ul>
     */
    @PostMapping("/{id}/cambiar-contrasena")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> cambiarContrasena(
            @PathVariable String id,
            @RequestBody Map<String, String> credenciales,
            Authentication authentication) {
        try {
            Usuario usuarioActual = usuarioService.obtenerPorUsername(authentication.getName());
            
            // Solo puede cambiar su propia contraseña
            if (!usuarioActual.getId().equals(id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No tienes permiso para cambiar la contraseña de otro usuario"));
            }

            String contraseniaActual = credenciales.get("contraseniaActual");
            String contrasenianueva = credenciales.get("contrasenianueva");
            String confirmarContrasenia = credenciales.get("confirmarContrasenia");

            // Verificar contraseña actual
            if (!passwordEncoder.matches(contraseniaActual, usuarioActual.getPassword())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La contraseña actual es incorrecta"));
            }

            // Verificar que las nuevas contraseñas coincidan
            if (!contrasenianueva.equals(confirmarContrasenia)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Las nuevas contraseñas no coinciden"));
            }

            // Actualizar contraseña
            usuarioActual.setPassword(passwordEncoder.encode(contrasenianueva));
            usuarioService.guardarUsuario(usuarioActual);

            return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Elimina un usuario del sistema.
     * <ul>
     *   <li><b>URL:</b> {@code DELETE /api/usuarios/{id}}</li>
     *   <li><b>Acceso:</b> el propio usuario (auto-borrado) o rol {@code ADMIN}</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con mensaje de confirmación; {@code 403}/{@code 400} en error</li>
     * </ul>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> eliminarUsuario(
            @PathVariable String id,
            Authentication authentication) {
        try {
            Usuario usuarioActual = usuarioService.obtenerPorUsername(authentication.getName());
            
            // Solo puede eliminarse a sí mismo o si es ADMIN
            boolean esAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            
            if (!usuarioActual.getId().equals(id) && !esAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No tienes permiso para eliminar este usuario"));
            }

            usuarioService.eliminarUsuario(id);
            return ResponseEntity.ok(Map.of("mensaje", "Usuario eliminado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
