/**
 * Capa de acceso a datos para {@link com.proyecto.aula.playgroundv2.model.Wishlist}.
 * <p>
 * Permite obtener o crear la lista de deseos asociada a un usuario mediante
 * {@link #findByUsuarioId(String)}. La unicidad de {@code usuarioId} está garantizada
 * por el índice en la entidad.
 * </p>
 */
package com.proyecto.aula.playgroundv2.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.proyecto.aula.playgroundv2.model.Wishlist;
import java.util.Optional;

/**
 * Repositorio MongoDB de listas de deseos (una por usuario).
 */
public interface WishlistRepository extends MongoRepository<Wishlist, String> {

    /**
     * Obtiene la wishlist del usuario indicado.
     *
     * @param usuarioId {@link com.proyecto.aula.playgroundv2.model.Usuario#getId()}
     * @return documento wishlist si el usuario ya tiene lista creada
     */
    Optional<Wishlist> findByUsuarioId(String usuarioId);
}
