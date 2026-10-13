/**
 * Lista de deseos de un usuario: referencias a juegos del catálogo.
 * <p>
 * Cada documento en la colección {@code wishlist} pertenece a un único usuario
 * ({@link #usuarioId}) y mantiene una lista ordenada de identificadores de juegos
 * ({@link #juegoIds}). La relación con {@link Juego} es por ID, no embebida.
 * </p>
 *
 * @see com.proyecto.aula.playgroundv2.repository.WishlistRepository
 * @see Juego
 */
package com.proyecto.aula.playgroundv2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Documento MongoDB: una wishlist por usuario (índice único en {@code usuarioId}).
 */
@Document(collection = "wishlist")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Wishlist {

    /** Identificador del documento wishlist. */
    @Id
    private String id;

    /**
     * Referencia al {@link Usuario#getId()}; un solo documento wishlist por usuario.
     */
    @Indexed(unique = true)
    private String usuarioId;

    /**
     * Identificadores de {@link Juego} guardados en la lista de deseos.
     * Por defecto lista vacía al construir con Lombok {@code @Builder}.
     */
    @Builder.Default
    private List<String> juegoIds = new ArrayList<>();
}
