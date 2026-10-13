package com.proyecto.aula.playgroundv2.controller;

import com.proyecto.aula.playgroundv2.service.JuegoService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.proyecto.aula.playgroundv2.model.Juego;

/**
 * Controlador MVC del buscador avanzado de juegos.
 * <p>
 * Ruta base: {@code /buscar}. Permite combinar filtros por nombre, género y plataforma de venta.
 * La ruta {@code /buscar} está en {@code permitAll} en la configuración de seguridad.
 * </p>
 *
 * @see JuegoService#buscarAvanzado
 */
@Controller
@RequestMapping("/buscar")
public class BuscadorController {

    private final JuegoService juegoService;

    public BuscadorController(JuegoService juegoService) {
        this.juegoService = juegoService;
    }

    /**
     * Ejecuta una búsqueda avanzada y muestra los resultados paginados.
     * <ul>
     *   <li><b>URL:</b> {@code GET /buscar}</li>
     *   <li><b>Acceso:</b> público (sin login)</li>
     *   <li><b>Parámetros:</b> {@code nombre}, {@code genero}, {@code plataformaVenta} (todos opcionales);
     *       {@code page} (0), {@code size} (12)</li>
     *   <li><b>Retorno:</b> vista {@code buscador} con {@code resultados} ({@code Page<Juego>})
     *       y los criterios de búsqueda en el modelo para repoblar el formulario</li>
     * </ul>
     */
    @GetMapping
    public String buscar(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String genero,
            @RequestParam(required = false) String plataformaVenta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model) {

        Page<Juego> resultados = juegoService.buscarAvanzado(nombre, genero, plataformaVenta, page, size);

        model.addAttribute("resultados", resultados);
        model.addAttribute("nombre", nombre);
        model.addAttribute("genero", genero);
        model.addAttribute("plataformaVenta", plataformaVenta);

        return "buscador";
    }
}
