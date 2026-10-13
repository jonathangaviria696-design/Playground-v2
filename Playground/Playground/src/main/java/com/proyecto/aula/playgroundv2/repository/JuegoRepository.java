/**
 * Capa de acceso a datos para la entidad {@link com.proyecto.aula.playgroundv2.model.Juego}.
 * <p>
 * Extiende {@link org.springframework.data.mongodb.repository.MongoRepository} y declara
 * consultas derivadas por nombre de método (Spring Data genera la implementación en tiempo de ejecución).
 * Soporta listados paginados, filtros por género/desarrolladora y búsqueda por título.
 * </p>
 */
package com.proyecto.aula.playgroundv2.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.proyecto.aula.playgroundv2.model.Juego;
import java.util.List;

/**
 * Repositorio MongoDB del catálogo de juegos.
 */
@Repository
public interface JuegoRepository extends MongoRepository<Juego, String> {

    /**
     * Juegos que coinciden exactamente con género y desarrolladora (paginado).
     */
    Page<Juego> findByGeneroAndDesarrolladora(String genero, String desarrolladora, Pageable pageable);

    /** Filtro por género sin distinguir mayúsculas/minúsculas. */
    Page<Juego> findByGeneroIgnoreCase(String genero, Pageable pageable);

    /** Filtro por desarrolladora sin distinguir mayúsculas/minúsculas. */
    Page<Juego> findByDesarrolladoraIgnoreCase(String desarrolladora, Pageable pageable);

    /** Búsqueda parcial por nombre del juego (contiene, ignore case). */
    Page<Juego> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    /** Búsqueda por nombre y género simultáneamente. */
    Page<Juego> findByNombreContainingIgnoreCaseAndGeneroIgnoreCase(String nombre, String genero, Pageable pageable);

    /**
     * Recupera varios juegos por lista de identificadores (por ejemplo wishlist).
     * Spring Data mapea {@code findByIdIn} al campo {@code _id} de MongoDB.
     *
     * @param ids lista de {@link Juego#getJuegoId()} / {@code _id}
     * @return juegos encontrados; puede ser menor que {@code ids.size()} si algún id no existe
     */
    List<Juego> findByIdIn(List<String> ids);
}
