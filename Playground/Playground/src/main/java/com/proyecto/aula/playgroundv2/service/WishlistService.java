/**
 * Capa de servicio para listas de deseos (wishlist) por usuario.
 * <p>
 * Cada usuario tiene como máximo un documento {@link com.proyecto.aula.playgroundv2.model.Wishlist}
 * con una lista de ids de juegos. Las operaciones de lectura resuelven esos ids a entidades
 * {@link com.proyecto.aula.playgroundv2.model.Juego} mediante {@link JuegoService}.
 * </p>
 *
 * @author proyecto-aula
 */
package com.proyecto.aula.playgroundv2.service;

import com.proyecto.aula.playgroundv2.model.Juego;
import com.proyecto.aula.playgroundv2.model.Wishlist;
import com.proyecto.aula.playgroundv2.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestiona la wishlist: alta/baja de juegos y consulta del catálogo asociado.
 * <p>
 * Patrón "get or create": si el usuario aún no tiene wishlist, se crea una vacía al primer uso.
 * </p>
 */
@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final JuegoService juegoService;

    /**
     * Inyección del repositorio de wishlists y del servicio de juegos para hidratar ids.
     */
    public WishlistService(WishlistRepository wishlistRepository, JuegoService juegoService) {
        this.wishlistRepository = wishlistRepository;
        this.juegoService = juegoService;
    }

    // --- Acceso interno al documento wishlist ---

    /**
     * Obtiene la wishlist del usuario o la crea con lista de ids vacía.
     * <p>
     * Cómo funciona: consulta por {@code usuarioId}; si no existe, persiste un nuevo
     * {@link Wishlist} con {@link ArrayList} mutable para poder añadir ids después.
     * </p>
     */
    private Wishlist getOrCreate(String usuarioId) {
        return wishlistRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> wishlistRepository.save(
                        Wishlist.builder().usuarioId(usuarioId).juegoIds(new ArrayList<>()).build()
                ));
    }

    // --- Operaciones públicas ---

    /**
     * Añade un juego a la wishlist si aún no estaba (evita duplicados en la lista de ids).
     *
     * @param usuarioId id del usuario autenticado
     * @param juegoId   id del juego a guardar
     */
    public void agregar(String usuarioId, String juegoId) {
        Wishlist w = getOrCreate(usuarioId);
        if (!w.getJuegoIds().contains(juegoId)) {
            w.getJuegoIds().add(juegoId);
            wishlistRepository.save(w);
        }
    }

    /**
     * Quita un juego de la wishlist (si no existía el id, la operación sigue siendo idempotente).
     */
    public void eliminar(String usuarioId, String juegoId) {
        Wishlist w = getOrCreate(usuarioId);
        w.getJuegoIds().remove(juegoId);
        wishlistRepository.save(w);
    }

    /**
     * Indica si un juego está en la wishlist del usuario.
     *
     * @return {@code false} si el usuario no tiene documento wishlist aún
     */
    public boolean contiene(String usuarioId, String juegoId) {
        return wishlistRepository.findByUsuarioId(usuarioId)
                .map(w -> w.getJuegoIds().contains(juegoId))
                .orElse(false);
    }

    /**
     * Devuelve los juegos completos asociados a la wishlist (consulta por lote de ids).
     *
     * @return lista vacía si no hay ids guardados
     */
    public List<Juego> obtenerJuegos(String usuarioId) {
        Wishlist w = getOrCreate(usuarioId);
        if (w.getJuegoIds().isEmpty()) return new ArrayList<>();
        return juegoService.obtenerPorIds(w.getJuegoIds());
    }
}
