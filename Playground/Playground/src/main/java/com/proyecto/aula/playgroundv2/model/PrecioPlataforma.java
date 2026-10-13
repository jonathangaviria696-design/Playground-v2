/**
 * Modelo embebido que representa el precio de un juego en una tienda o plataforma concreta.
 * <p>
 * No es un documento independiente en MongoDB: se persiste como elemento dentro de la lista
 * {@code precios} del documento {@link Juego}. Se usa en vistas y cálculos de precio mínimo.
 * </p>
 *
 * @see Juego#getPrecios()
 * @see Juego#getPrecioMinimo()
 */
package com.proyecto.aula.playgroundv2.model;

/**
 * Par (plataforma, precio) asociado a un juego del catálogo.
 */
public class PrecioPlataforma {

    /** Nombre de la tienda o plataforma (por ejemplo Steam, Epic, GOG). */
    private String plataforma;

    /** Precio en la moneda acordada por la aplicación; puede ser {@code null} si no está definido. */
    private Double precio;

    /** Constructor sin argumentos requerido por frameworks de serialización y MongoDB. */
    public PrecioPlataforma() {}

    /**
     * Constructor con todos los campos útiles para pruebas o creación programática.
     *
     * @param plataforma identificador legible de la tienda
     * @param precio     importe; puede ser {@code null}
     */
    public PrecioPlataforma(String plataforma, Double precio) {
        this.plataforma = plataforma;
        this.precio = precio;
    }

    public String getPlataforma() { return plataforma; }
    public void setPlataforma(String plataforma) { this.plataforma = plataforma; }

    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }
}
