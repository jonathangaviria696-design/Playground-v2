package com.proyecto.aula.playgroundv2.controller;

import com.proyecto.aula.playgroundv2.model.*;
import com.proyecto.aula.playgroundv2.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

/**
 * Controlador MVC de la lista de deseos (wishlist) del usuario.
 * <p>
 * Ruta base: {@code /wishlist}. Todas las operaciones requieren usuario autenticado
 * ({@code authenticated} en {@link com.proyecto.aula.playgroundv2.config.SecurityConfig}).
 * Las acciones de agregar/eliminar redirigen de vuelta al detalle del juego o a la wishlist.
 * </p>
 *
 * @see WishlistService
 */
@Controller
@RequestMapping("/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;
    private final UsuarioService usuarioService;

    public WishlistController(WishlistService wishlistService, UsuarioService usuarioService) {
        this.wishlistService = wishlistService;
        this.usuarioService = usuarioService;
    }

    /**
     * Muestra todos los juegos guardados en la wishlist del usuario en sesión.
     * <ul>
     *   <li><b>URL:</b> {@code GET /wishlist}</li>
     *   <li><b>Acceso:</b> usuario autenticado</li>
     *   <li><b>Retorno:</b> vista {@code wishlist} con {@code List<Juego>} en el atributo {@code juegos}</li>
     * </ul>
     */
    @GetMapping
    public String verWishlist(Authentication authentication, Model model) {
        Usuario usuario = usuarioService.obtenerPorUsername(authentication.getName());
        List<Juego> juegos = wishlistService.obtenerJuegos(usuario.getId());
        model.addAttribute("juegos", juegos);
        return "wishlist";
    }

    /**
     * Añade un juego a la wishlist del usuario autenticado.
     * <ul>
     *   <li><b>URL:</b> {@code POST /wishlist/agregar/{juegoId}}</li>
     *   <li><b>Acceso:</b> usuario autenticado</li>
     *   <li><b>Retorno:</b> redirección a {@code /juegos/{juegoId}} con mensaje flash de confirmación</li>
     * </ul>
     */
    @PostMapping("/agregar/{juegoId}")
    public String agregar(@PathVariable String juegoId, Authentication authentication,
                          RedirectAttributes ra) {
        Usuario usuario = usuarioService.obtenerPorUsername(authentication.getName());
        wishlistService.agregar(usuario.getId(), juegoId);
        ra.addFlashAttribute("mensaje", "Juego añadido a tu lista de deseos");
        return "redirect:/juegos/" + juegoId;
    }

    /**
     * Elimina un juego de la wishlist del usuario autenticado.
     * <ul>
     *   <li><b>URL:</b> {@code POST /wishlist/eliminar/{juegoId}}</li>
     *   <li><b>Acceso:</b> usuario autenticado</li>
     *   <li><b>Retorno:</b> redirección a {@code /wishlist} con mensaje flash</li>
     * </ul>
     */
    @PostMapping("/eliminar/{juegoId}")
    public String eliminar(@PathVariable String juegoId, Authentication authentication,
                           RedirectAttributes ra) {
        Usuario usuario = usuarioService.obtenerPorUsername(authentication.getName());
        wishlistService.eliminar(usuario.getId(), juegoId);
        ra.addFlashAttribute("mensaje", "Juego eliminado de tu lista de deseos");
        return "redirect:/wishlist";
    }
}
