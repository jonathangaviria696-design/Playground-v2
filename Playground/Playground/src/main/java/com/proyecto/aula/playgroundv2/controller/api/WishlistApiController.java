package com.proyecto.aula.playgroundv2.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.proyecto.aula.playgroundv2.model.Juego;
import com.proyecto.aula.playgroundv2.model.Usuario;
import com.proyecto.aula.playgroundv2.service.UsuarioService;
import com.proyecto.aula.playgroundv2.service.WishlistService;

/**
 * API REST de la lista de deseos del usuario autenticado.
 * <p>
 * Ruta base: {@code /api/wishlist}. La clase exige autenticación global
 * ({@link PreAuthorize @PreAuthorize("isAuthenticated()")}) y el filtro HTTP
 * también marca {@code /api/wishlist/**} como {@code authenticated}.
 * Todas las operaciones actúan sobre la wishlist del usuario del token JWT o sesión.
 * </p>
 *
 * @see WishlistService
 */
@RestController
@RequestMapping("/api/wishlist")
@PreAuthorize("isAuthenticated()")
public class WishlistApiController {

    private final WishlistService wishlistService;
    private final UsuarioService usuarioService;

    public WishlistApiController(WishlistService wishlistService, UsuarioService usuarioService) {
        this.wishlistService = wishlistService;
        this.usuarioService = usuarioService;
    }

    /**
     * Obtiene todos los juegos de la wishlist del usuario autenticado.
     * <ul>
     *   <li><b>URL:</b> {@code GET /api/wishlist}</li>
     *   <li><b>Acceso:</b> usuario autenticado</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con {@code List<Juego>} en JSON</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<List<Juego>> obtenerWishlist(Authentication authentication) {
        Usuario usuario = usuarioService.obtenerPorUsername(authentication.getName());
        List<Juego> juegos = wishlistService.obtenerJuegos(usuario.getId());
        return ResponseEntity.ok(juegos);
    }

    /**
     * Comprueba si un juego concreto está en la wishlist.
     * <ul>
     *   <li><b>URL:</b> {@code GET /api/wishlist/{juegoId}}</li>
     *   <li><b>Acceso:</b> usuario autenticado</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con {@code {"enWishlist": true|false}}</li>
     * </ul>
     */
    @GetMapping("/{juegoId}")
    public ResponseEntity<Map<String, Boolean>> verificarEnWishlist(
            @PathVariable String juegoId,
            Authentication authentication) {
        Usuario usuario = usuarioService.obtenerPorUsername(authentication.getName());
        boolean enWishlist = wishlistService.contiene(usuario.getId(), juegoId);
        return ResponseEntity.ok(Map.of("enWishlist", enWishlist));
    }

    /**
     * Añade un juego a la wishlist.
     * <ul>
     *   <li><b>URL:</b> {@code POST /api/wishlist/{juegoId}}</li>
     *   <li><b>Acceso:</b> usuario autenticado</li>
     *   <li><b>Retorno:</b> {@code 201 Created} con mensaje de confirmación;
     *       {@code 400 Bad Request} con {@code error} si falla la operación</li>
     * </ul>
     */
    @PostMapping("/{juegoId}")
    public ResponseEntity<Map<String, String>> agregarAWishlist(
            @PathVariable String juegoId,
            Authentication authentication) {
        try {
            Usuario usuario = usuarioService.obtenerPorUsername(authentication.getName());
            wishlistService.agregar(usuario.getId(), juegoId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("mensaje", "Juego agregado a la wishlist"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Elimina un juego de la wishlist.
     * <ul>
     *   <li><b>URL:</b> {@code DELETE /api/wishlist/{juegoId}}</li>
     *   <li><b>Acceso:</b> usuario autenticado</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con mensaje; {@code 400} en caso de error</li>
     * </ul>
     */
    @DeleteMapping("/{juegoId}")
    public ResponseEntity<Map<String, String>> eliminarDeWishlist(
            @PathVariable String juegoId,
            Authentication authentication) {
        try {
            Usuario usuario = usuarioService.obtenerPorUsername(authentication.getName());
            wishlistService.eliminar(usuario.getId(), juegoId);
            return ResponseEntity.ok(Map.of("mensaje", "Juego eliminado de la wishlist"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Vacía por completo la wishlist del usuario (elimina todos los juegos uno a uno).
     * <ul>
     *   <li><b>URL:</b> {@code POST /api/wishlist/limpiar}</li>
     *   <li><b>Acceso:</b> usuario autenticado</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con mensaje de confirmación; {@code 400} si hay error</li>
     * </ul>
     */
    @PostMapping("/limpiar")
    public ResponseEntity<Map<String, String>> limpiarWishlist(Authentication authentication) {
        try {
            Usuario usuario = usuarioService.obtenerPorUsername(authentication.getName());
            List<Juego> juegos = wishlistService.obtenerJuegos(usuario.getId());
            for (Juego juego : juegos) {
                wishlistService.eliminar(usuario.getId(), juego.getJuegoId());
            }
            return ResponseEntity.ok(Map.of("mensaje", "Wishlist limpiada correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
