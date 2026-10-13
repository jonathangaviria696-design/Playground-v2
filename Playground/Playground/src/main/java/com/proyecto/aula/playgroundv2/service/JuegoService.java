/**
 * Capa de servicio para la gestión de juegos en el catálogo.
 * <p>
 * Centraliza las operaciones de consulta (con filtros, búsqueda avanzada y paginación),
 * creación, lectura y eliminación de entidades {@link com.proyecto.aula.playgroundv2.model.Juego}.
 * Combina el repositorio Spring Data con {@link org.springframework.data.mongodb.core.MongoTemplate}
 * cuando hace falta consultas dinámicas por criterios múltiples.
 * </p>
 *
 * @author proyecto-aula
 * @see com.proyecto.aula.playgroundv2.repository.JuegoRepository
 */
package com.proyecto.aula.playgroundv2.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import com.proyecto.aula.playgroundv2.model.Juego;
import com.proyecto.aula.playgroundv2.repository.JuegoRepository;
import java.util.List;

/**
 * Servicio de dominio para juegos: filtrado, búsqueda, CRUD y agregaciones simples.
 * <p>
 * Los métodos de listado devuelven {@link Page} o listas acotadas según el caso de uso
 * (API REST, vistas Thymeleaf, wishlist, etc.).
 * </p>
 */
@Service
public class JuegoService {

    /** Repositorio Spring Data para operaciones CRUD y consultas derivadas por nombre de método. */
    private final JuegoRepository juegosRepository;

    /** Plantilla MongoDB para consultas dinámicas (regex, criterios anidados en precios). */
    private final MongoTemplate mongoTemplate;

    /**
     * Inyección de dependencias por constructor (recomendado en Spring).
     *
     * @param juegosRepository acceso a la colección de juegos
     * @param mongoTemplate      ejecutor de consultas Mongo personalizadas
     */
    public JuegoService(JuegoRepository juegosRepository, MongoTemplate mongoTemplate) {
        this.juegosRepository = juegosRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // --- Consultas con filtros simples ---

    /**
     * Filtra juegos por género y/o desarrolladora con paginación.
     * <p>
     * Cómo funciona: evalúa qué parámetros vienen informados y delega en el método
     * del repositorio más específico; si ninguno viene, devuelve todos los juegos paginados.
     * </p>
     *
     * @param genero         género a filtrar (opcional, insensible a mayúsculas en repo)
     * @param desarrolladora desarrolladora a filtrar (opcional)
     * @param page           índice de página (base 0)
     * @param size           tamaño de página
     * @return página de juegos que cumplen los criterios
     */
    public Page<Juego> filtrar(String genero, String desarrolladora, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        boolean hasGenero = genero != null && !genero.isBlank();
        boolean hasDev = desarrolladora != null && !desarrolladora.isBlank();

        if (hasGenero && hasDev)
            return juegosRepository.findByGeneroAndDesarrolladora(genero, desarrolladora, pageable);
        if (hasGenero)
            return juegosRepository.findByGeneroIgnoreCase(genero, pageable);
        if (hasDev)
            return juegosRepository.findByDesarrolladoraIgnoreCase(desarrolladora, pageable);
        return juegosRepository.findAll(pageable);
    }

    // --- Búsqueda avanzada con MongoTemplate ---

    /**
     * Búsqueda avanzada por nombre, género y plataforma de venta (dentro del array de precios).
     * <p>
     * Cómo funciona: construye un {@link Criteria} dinámico; el nombre y género usan regex
     * case-insensitive; la plataforma coincide con el campo anidado {@code precios.plataforma}.
     * Primero cuenta el total con {@code count}, luego aplica skip/limit vía {@link PageRequest}.
     * </p>
     *
     * @param nombre           fragmento del nombre (opcional)
     * @param genero           fragmento del género (opcional)
     * @param plataformaVenta  nombre exacto de plataforma en precios (opcional)
     * @param page             índice de página
     * @param size             tamaño de página
     * @return página construida manualmente con {@link PageImpl}
     */
    public Page<Juego> buscarAvanzado(String nombre, String genero, String plataformaVenta,
                                       int page, int size) {
        Criteria criteria = new Criteria();

        if (nombre != null && !nombre.isBlank())
            criteria.and("nombre").regex(nombre.trim(), "i");
        if (genero != null && !genero.isBlank())
            criteria.and("genero").regex(genero.trim(), "i");
        if (plataformaVenta != null && !plataformaVenta.isBlank())
            criteria.and("precios.plataforma").is(plataformaVenta.trim());

        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, Juego.class);

        query.with(PageRequest.of(page, size));
        List<Juego> results = mongoTemplate.find(query, Juego.class);

        return new PageImpl<>(results, PageRequest.of(page, size), total);
    }

    // --- Operaciones CRUD y listados ---

    /**
     * Persiste un juego nuevo o actualiza uno existente si ya trae {@code id}.
     *
     * @param juego entidad a guardar
     * @return juego persistido (con id asignado si era nuevo)
     */
    public Juego crear(Juego juego) {
        return juegosRepository.save(juego);
    }

    /**
     * Obtiene un juego por identificador MongoDB.
     *
     * @param id identificador del documento
     * @return juego encontrado
     * @throws RuntimeException si no existe
     */
    public Juego obtenerPorId(String id) {
        return juegosRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Juego no encontrado: " + id));
    }

    /**
     * Elimina un juego por id.
     *
     * @param id identificador del juego
     */
    public void eliminar(String id) {
        juegosRepository.deleteById(id);
    }

    /**
     * Devuelve todos los juegos sin paginación (uso interno o exportaciones pequeñas).
     */
    public Iterable<Juego> obtenerTodos() {
        return juegosRepository.findAll();
    }

    /**
     * Lista todos los juegos con paginación estándar de Spring Data.
     *
     * @param page índice de página
     * @param size elementos por página
     */
    public Page<Juego> obtenerTodosPaginados(int page, int size) {
        return juegosRepository.findAll(PageRequest.of(page, size));
    }

    /**
     * Obtiene hasta {@code limite} juegos de un género concreto (p. ej. carrusel en home).
     *
     * @param genero  género a buscar
     * @param limite  máximo de resultados (solo primera página)
     */
    public List<Juego> obtenerPorGenero(String genero, int limite) {
        return juegosRepository.findByGeneroIgnoreCase(genero, PageRequest.of(0, limite)).getContent();
    }

    /**
     * Resuelve varios juegos a partir de una lista de ids (p. ej. wishlist).
     *
     * @param ids lista de identificadores MongoDB
     * @return juegos encontrados (puede ser menor que ids si alguno no existe)
     */
    public List<Juego> obtenerPorIds(List<String> ids) {
        return juegosRepository.findByIdIn(ids);
    }

    /**
     * Cuenta el total de documentos en la colección de juegos.
     *
     * @return número total de juegos
     */
    public long contarTotal() { return juegosRepository.count(); }
}
