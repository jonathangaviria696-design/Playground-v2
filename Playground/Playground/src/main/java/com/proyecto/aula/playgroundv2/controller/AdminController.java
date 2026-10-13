package com.proyecto.aula.playgroundv2.controller;

import com.proyecto.aula.playgroundv2.model.Juego;
import com.proyecto.aula.playgroundv2.model.PrecioPlataforma;
import com.proyecto.aula.playgroundv2.service.JuegoService;
import com.proyecto.aula.playgroundv2.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Panel de administración web para gestionar el catálogo de juegos.
 * <p>
 * Ruta base: {@code /admin}. Solo usuarios con rol {@code ADMIN} pueden acceder
 * ({@code hasRole('ADMIN')} en la configuración de seguridad). Renderiza vistas Thymeleaf
 * y procesa formularios multipart para imágenes de juegos.
 * </p>
 *
 * @see JuegoService
 * @see UsuarioService
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final JuegoService juegoService;
    private final UsuarioService usuarioService;


    @Value("${app.upload.dir:src/main/resources/static/uploads}")
    private String uploadDir;

    public AdminController(JuegoService juegoService, UsuarioService usuarioService) {
        this.juegoService = juegoService;
        this.usuarioService = usuarioService;
    }

    /**
     * Panel principal con estadísticas y listado de juegos.
     * <ul>
     *   <li><b>URL:</b> {@code GET /admin}</li>
     *   <li><b>Acceso:</b> rol {@code ADMIN}</li>
     *   <li><b>Retorno:</b> vista {@code admin-dashboard} con totales de usuarios/juegos,
     *       lista completa de juegos y {@code masVendidos} (vacío por defecto)</li>
     * </ul>
     */
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("totalUsuarios", usuarioService.contarTotal());
        model.addAttribute("totalJuegos", juegoService.contarTotal());
        model.addAttribute("juegos", juegoService.obtenerTodos());
        // admin-dashboard.html usa masVendidos; si falta, Thymeleaf lanza NPE al evaluar .isEmpty()
        model.addAttribute("masVendidos", List.of());
        return "admin-dashboard";
    }

    /**
     * Formulario vacío para crear un juego nuevo.
     * <ul>
     *   <li><b>URL:</b> {@code GET /admin/juego/nuevo}</li>
     *   <li><b>Acceso:</b> rol {@code ADMIN}</li>
     *   <li><b>Retorno:</b> vista {@code admin-juego-formulario} con entidad {@code Juego} vacía</li>
     * </ul>
     */
    @GetMapping("/juego/nuevo")
    public String formularioNuevo(Model model) {
        model.addAttribute("juego", new Juego());
        return "admin-juego-formulario";
    }

    /**
     * Formulario de edición cargado con los datos de un juego existente.
     * <ul>
     *   <li><b>URL:</b> {@code GET /admin/juego/{id}/editar}</li>
     *   <li><b>Acceso:</b> rol {@code ADMIN}</li>
     *   <li><b>Retorno:</b> vista {@code admin-juego-formulario} con el {@code Juego} solicitado</li>
     * </ul>
     */
    @GetMapping("/juego/{id}/editar")
    public String formularioEditar(@PathVariable String id, Model model) {
        model.addAttribute("juego", juegoService.obtenerPorId(id));
        return "admin-juego-formulario";
    }

    /**
     * Crea o actualiza un juego desde el formulario de administración.
     * <ul>
     *   <li><b>URL:</b> {@code POST /admin/juego/guardar}</li>
     *   <li><b>Acceso:</b> rol {@code ADMIN}</li>
     *   <li><b>Cuerpo:</b> parámetros de formulario (nombre, descripción, género, imagen URL/archivo,
     *       precios por plataforma, etc.)</li>
     *   <li><b>Retorno:</b> redirección a {@code /admin} con mensaje flash de éxito o error de subida de imagen</li>
     * </ul>
     */
    @PostMapping("/juego/guardar")
    public String guardar(
            @RequestParam(value = "juegoId",       required = false) String juegoId,
            @RequestParam(value = "nombre",         required = false) String nombre,
            @RequestParam(value = "descripcion",    required = false) String descripcion,
            @RequestParam(value = "desarrolladora", required = false) String desarrolladora,
            @RequestParam(value = "genero",         required = false) String genero,
            @RequestParam(value = "imagenUrl",      required = false) String imagenUrlTexto,
            @RequestParam(value = "fechaDeLanzamiento", required = false) String fechaStr,
            @RequestParam(value = "imagenArchivo",  required = false) MultipartFile imagenArchivo,
            @RequestParam(value = "plataformaNombre", required = false) List<String> nombresPlat,
            @RequestParam(value = "plataformaPrecio", required = false) List<String> preciosPlat,
            RedirectAttributes ra) {

        // --- Imagen: archivo subido tiene prioridad sobre URL ---
        String imagenUrl = emptyToNull(imagenUrlTexto);
        if (imagenArchivo != null && !imagenArchivo.isEmpty()) {
            try {
                imagenUrl = guardarImagen(imagenArchivo);
            } catch (IOException e) {
                ra.addFlashAttribute("error", "Error al subir la imagen: " + e.getMessage());
            }
        }

        // --- Fecha ---
        LocalDate fecha = null;
        if (fechaStr != null && !fechaStr.isBlank()) {
            try { fecha = LocalDate.parse(fechaStr); } catch (Exception ignored) {}
        }

        // --- Lista de precios por plataforma ---
        List<PrecioPlataforma> listaPrecios = new ArrayList<>();
        if (nombresPlat != null && preciosPlat != null) {
            for (int i = 0; i < nombresPlat.size(); i++) {
                String n = nombresPlat.get(i);
                if (n != null && !n.isBlank() && i < preciosPlat.size()) {
                    try {
                        double p = Double.parseDouble(preciosPlat.get(i));
                        listaPrecios.add(new PrecioPlataforma(n.trim(), p));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // --- Construir entidad ---
        Juego juego = new Juego();
        juego.setJuegoId(emptyToNull(juegoId));
        juego.setNombre(nombre);
        juego.setDescripcion(descripcion);
        juego.setDesarrolladora(desarrolladora);
        juego.setGenero(genero);
        juego.setFechaDeLanzamiento(fecha);
        juego.setImagenUrl(imagenUrl);
        juego.setPrecios(listaPrecios.isEmpty() ? null : listaPrecios);

        juegoService.crear(juego);
        ra.addFlashAttribute("mensaje", "Juego guardado correctamente.");
        return "redirect:/admin";
    }

    /**
     * Elimina un juego del catálogo.
     * <ul>
     *   <li><b>URL:</b> {@code POST /admin/juego/{id}/eliminar}</li>
     *   <li><b>Acceso:</b> rol {@code ADMIN}</li>
     *   <li><b>Retorno:</b> redirección a {@code /admin} con mensaje flash</li>
     * </ul>
     */
    @PostMapping("/juego/{id}/eliminar")
    public String eliminar(@PathVariable String id, RedirectAttributes ra) {
        juegoService.eliminar(id);
        ra.addFlashAttribute("mensaje", "Juego eliminado.");
        return "redirect:/admin";
    }

    /**
     * Borra la imagen local de un juego y limpia su URL en base de datos.
     * <ul>
     *   <li><b>URL:</b> {@code POST /admin/juego/{id}/eliminar-imagen}</li>
     *   <li><b>Acceso:</b> rol {@code ADMIN}</li>
     *   <li><b>Retorno:</b> redirección a {@code /admin/juego/{id}/editar} con mensaje flash de éxito o error</li>
     * </ul>
     */
    @PostMapping("/juego/{id}/eliminar-imagen")
    public String eliminarImagen(@PathVariable String id, RedirectAttributes ra) {
        try {
            Juego juego = juegoService.obtenerPorId(id);
            String imagenUrl = juego.getImagenUrl();

            // Solo borrar del disco si es una imagen subida (empieza con /uploads/)
            if (imagenUrl != null && imagenUrl.startsWith("/uploads/")) {
                String nombreArchivo = imagenUrl.substring("/uploads/".length());
                Path ruta = Paths.get(uploadDir).resolve(nombreArchivo);
                Files.deleteIfExists(ruta);
            }

            // Limpiar la URL en la base de datos
            juego.setImagenUrl(null);
            juegoService.crear(juego);

            ra.addFlashAttribute("mensaje", "Imagen eliminada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al eliminar la imagen: " + e.getMessage());
        }
        return "redirect:/admin/juego/" + id + "/editar";
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Guarda la imagen en disco y devuelve la URL pública relativa.
     * El archivo se almacena en static/uploads/ y se sirve como /uploads/{nombre}.
     */
    private String guardarImagen(MultipartFile archivo) throws IOException {
        // Nombre único: timestamp + nombre original saneado
        String nombreOriginal = Objects.requireNonNullElse(archivo.getOriginalFilename(), "imagen");
        String extension = "";
        int punto = nombreOriginal.lastIndexOf('.');
        if (punto >= 0) extension = nombreOriginal.substring(punto).toLowerCase();

        String nombreFinal = System.currentTimeMillis() + "_" +
                nombreOriginal.replaceAll("[^a-zA-Z0-9._-]", "_");

        Path destino = Paths.get(uploadDir).resolve(nombreFinal);
        Files.createDirectories(destino.getParent());
        Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/" + nombreFinal;
    }

    /** Convierte cadenas vacías o en blanco en {@code null} para persistencia opcional. */
    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
