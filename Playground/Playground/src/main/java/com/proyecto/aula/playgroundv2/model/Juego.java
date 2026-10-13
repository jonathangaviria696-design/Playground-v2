/**
 * Entidad de dominio que representa un videojuego del catálogo.
 * <p>
 * Se persiste en la colección MongoDB {@code juegos}. Incluye metadatos descriptivos,
 * precios por plataforma embebidos ({@link PrecioPlataforma}) y URLs de medios.
 * Los métodos {@link #getPrecioMinimo()} y {@link #getPlataformaMinima()} calculan
 * valores derivados en memoria y no se almacenan en base de datos.
 * </p>
 *
 * @see PrecioPlataforma
 * @see com.proyecto.aula.playgroundv2.repository.JuegoRepository
 */
package com.proyecto.aula.playgroundv2.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB de un juego: catálogo, búsquedas y detalle en la UI.
 */
@Document(collection = "juegos")
public class Juego {

    /** Identificador del documento; Spring Data MongoDB mapea {@code @Id} ↔ {@code _id} automáticamente. */
    @Id
    private String id;

    /** Título del juego; índice único para evitar duplicados en el catálogo. */
    @Indexed(unique = true)
    private String nombre;

    /** Resumen o ficha descriptiva mostrada en listados y detalle. */
    private String descripcion;

    /** Estudio o empresa desarrolladora. */
    private String desarrolladora;

    /** Fecha de lanzamiento comercial (sin zona horaria; solo calendario). */
    private LocalDate fechaDeLanzamiento;

    /** Género principal (acción, RPG, etc.) usado en filtros. */
    private String genero;

    /** Lista de precios por tienda/plataforma; documentos embebidos, no referencias. */
    private List<PrecioPlataforma> precios;

    /** URL de la imagen principal (portada). */
    private String imagenUrl;

    /** URLs de capturas adicionales para la galería del detalle. */
    private List<String> screenshots;

    /** Constructor vacío requerido por Spring Data y deserialización. */
    public Juego() {}

    /**
     * Devuelve el menor precio entre todas las plataformas con precio no nulo.
     * <p>
     * No se persiste: es un valor calculado para comparativas y vistas.
     * </p>
     *
     * @return precio mínimo, o {@code 0.0} si no hay precios o la lista está vacía
     */
    public double getPrecioMinimo() {
        if (precios == null || precios.isEmpty()) return 0.0;
        return precios.stream()
                .map(PrecioPlataforma::getPrecio)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0.0);
    }

    /**
     * Devuelve el nombre de la plataforma que ofrece el precio más bajo.
     * <p>
     * En empate, el stream de Java conserva el primer mínimo encontrado según el orden de la lista.
     * </p>
     *
     * @return nombre de plataforma con precio mínimo, o {@code null} si no hay precios válidos
     */
    public String getPlataformaMinima() {
        if (precios == null || precios.isEmpty()) return null;
        return precios.stream()
                .filter(p -> p.getPrecio() != null)
                .min(Comparator.comparing(PrecioPlataforma::getPrecio))
                .map(PrecioPlataforma::getPlataforma)
                .orElse(null);
    }

    /**
     * Alias semántico de {@link #id} para plantillas Thymeleaf y servicios legacy.
     * Evita romper referencias que usan {@code juegoId} en lugar de {@code id}.
     */
    public String getJuegoId() { return id; }

    /**
     * Establece el identificador mediante el alias {@code juegoId}.
     *
     * @param juegoId mismo valor que {@link #id}
     */
    public void setJuegoId(String juegoId) { this.id = juegoId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getDesarrolladora() { return desarrolladora; }
    public void setDesarrolladora(String desarrolladora) { this.desarrolladora = desarrolladora; }

    public LocalDate getFechaDeLanzamiento() { return fechaDeLanzamiento; }
    public void setFechaDeLanzamiento(LocalDate fechaDeLanzamiento) { this.fechaDeLanzamiento = fechaDeLanzamiento; }

    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }

    public List<PrecioPlataforma> getPrecios() { return precios; }
    public void setPrecios(List<PrecioPlataforma> precios) { this.precios = precios; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public List<String> getScreenshots() { return screenshots; }
    public void setScreenshots(List<String> screenshots) { this.screenshots = screenshots; }
}
