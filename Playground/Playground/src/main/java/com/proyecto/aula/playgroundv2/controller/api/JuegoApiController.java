package com.proyecto.aula.playgroundv2.controller.api;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.proyecto.aula.playgroundv2.model.Juego;
import com.proyecto.aula.playgroundv2.service.JuegoService;

/**
 * API REST del catálogo de juegos.
 * <p>
 * Ruta base: {@code /api/juegos}. Las operaciones de lectura (GET) son públicas
 * ({@code permitAll}). Crear, actualizar y eliminar exigen rol {@code ADMIN}
 * mediante {@link PreAuthorize}.
 * </p>
 *
 * @see JuegoService
 */
@RestController
@RequestMapping("/api/juegos")
public class JuegoApiController {

    private final JuegoService juegoService;

    public JuegoApiController(JuegoService juegoService) {
        this.juegoService = juegoService;
    }

    /**
     * Lista juegos con filtros opcionales y paginación.
     * <ul>
     *   <li><b>URL:</b> {@code GET /api/juegos}</li>
     *   <li><b>Acceso:</b> público</li>
     *   <li><b>Query:</b> {@code genero}, {@code desarrolladora}, {@code page}, {@code size}</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con {@code Page<Juego>} en JSON</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<Page<Juego>> listarJuegos(
            @RequestParam(required = false) String genero,
            @RequestParam(required = false) String desarrolladora,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Page<Juego> juegos = juegoService.filtrar(genero, desarrolladora, page, size);
        return ResponseEntity.ok(juegos);
    }

    /**
     * Obtiene un juego por su identificador.
     * <ul>
     *   <li><b>URL:</b> {@code GET /api/juegos/{id}}</li>
     *   <li><b>Acceso:</b> público</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con el {@code Juego}; {@code 404 Not Found} si no existe</li>
     * </ul>
     */
    @GetMapping("/{id}")
    public ResponseEntity<Juego> obtenerJuego(@PathVariable String id) {
        try {
            Juego juego = juegoService.obtenerPorId(id);
            return ResponseEntity.ok(juego);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Lista juegos filtrados por género.
     * <ul>
     *   <li><b>URL:</b> {@code GET /api/juegos/genero/{genero}}</li>
     *   <li><b>Acceso:</b> público</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con {@code Page<Juego>}</li>
     * </ul>
     */
    @GetMapping("/genero/{genero}")
    public ResponseEntity<Page<Juego>> juegosPorGenero(
            @PathVariable String genero,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Page<Juego> juegos = juegoService.filtrar(genero, null, page, size);
        return ResponseEntity.ok(juegos);
    }

    /**
     * Búsqueda avanzada por nombre, género y plataforma de venta.
     * <ul>
     *   <li><b>URL:</b> {@code GET /api/juegos/buscar}</li>
     *   <li><b>Acceso:</b> público</li>
     *   <li><b>Query:</b> {@code nombre}, {@code genero}, {@code plataformaVenta}, {@code page}, {@code size}</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con {@code Page<Juego>}</li>
     * </ul>
     */
    @GetMapping("/buscar")
    public ResponseEntity<Page<Juego>> buscarJuegos(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String genero,
            @RequestParam(required = false) String plataformaVenta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Page<Juego> resultados = juegoService.buscarAvanzado(nombre, genero, plataformaVenta, page, size);
        return ResponseEntity.ok(resultados);
    }

    /**
     * Crea un juego nuevo en el catálogo.
     * <ul>
     *   <li><b>URL:</b> {@code POST /api/juegos}</li>
     *   <li><b>Acceso:</b> rol {@code ADMIN} (JWT o sesión con {@code ROLE_ADMIN})</li>
     *   <li><b>Cuerpo:</b> JSON con entidad {@code Juego}</li>
     *   <li><b>Retorno:</b> {@code 201 Created} con el juego persistido</li>
     * </ul>
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Juego> crearJuego(@RequestBody Juego juego) {
        Juego juegoCreado = juegoService.crear(juego);
        return ResponseEntity.status(HttpStatus.CREATED).body(juegoCreado);
    }

    /**
     * Actualiza un juego existente (upsert por id en ruta).
     * <ul>
     *   <li><b>URL:</b> {@code PUT /api/juegos/{id}}</li>
     *   <li><b>Acceso:</b> rol {@code ADMIN}</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con el juego actualizado; {@code 404} si falla la operación</li>
     * </ul>
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Juego> actualizarJuego(@PathVariable String id, @RequestBody Juego juego) {
        try {
            juego.setJuegoId(id);
            Juego juegoActualizado = juegoService.crear(juego);
            return ResponseEntity.ok(juegoActualizado);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Elimina un juego del catálogo.
     * <ul>
     *   <li><b>URL:</b> {@code DELETE /api/juegos/{id}}</li>
     *   <li><b>Acceso:</b> rol {@code ADMIN}</li>
     *   <li><b>Retorno:</b> {@code 200 OK} con {@code {"mensaje": "Juego eliminado correctamente"}};
     *       {@code 404} si no se encuentra</li>
     * </ul>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> eliminarJuego(@PathVariable String id) {
        try {
            juegoService.eliminar(id);
            return ResponseEntity.ok(Map.of("mensaje", "Juego eliminado correctamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
