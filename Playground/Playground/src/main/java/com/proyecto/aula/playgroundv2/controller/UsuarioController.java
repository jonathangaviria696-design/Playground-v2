package com.proyecto.aula.playgroundv2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.proyecto.aula.playgroundv2.dto.RegistroUsuarioForm;
import com.proyecto.aula.playgroundv2.service.JuegoService;
import com.proyecto.aula.playgroundv2.service.UsuarioService;

import jakarta.validation.Valid;

/**
 * Controlador MVC de autenticación, registro y página de inicio del sitio.
 * <p>
 * Gestiona login, registro de nuevos usuarios y el dashboard principal con destacados por género.
 * Las rutas {@code /}, {@code /login}, {@code /registro} y {@code /dashboard} son públicas
 * ({@code permitAll}); el resto de peticiones no listadas exigen sesión autenticada.
 * </p>
 *
 * @see UsuarioService
 * @see JuegoService
 */
@Controller
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final JuegoService juegosService;

    public UsuarioController(UsuarioService usuarioService, JuegoService juegosService) {
        this.usuarioService = usuarioService;
        this.juegosService = juegosService;
    }

    /**
     * Muestra el formulario de inicio de sesión.
     * <ul>
     *   <li><b>URL:</b> {@code GET /login}</li>
     *   <li><b>Acceso:</b> público; Spring Security redirige aquí a usuarios no autenticados</li>
     *   <li><b>Retorno:</b> vista {@code login}</li>
     * </ul>
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Muestra el formulario de registro de nuevo usuario.
     * <ul>
     *   <li><b>URL:</b> {@code GET /registro}</li>
     *   <li><b>Acceso:</b> público</li>
     *   <li><b>Retorno:</b> vista {@code registro} con {@code RegistroUsuarioForm} vacío en el modelo</li>
     * </ul>
     */
    @GetMapping("/registro")
    public String mostrarRegistro(Model model) {
        model.addAttribute("registroUsuarioForm", new RegistroUsuarioForm());
        return "registro";
    }

    /**
     * Procesa el alta de un nuevo usuario.
     * <ul>
     *   <li><b>URL:</b> {@code POST /registro}</li>
     *   <li><b>Acceso:</b> público</li>
     *   <li><b>Retorno:</b> redirección a {@code /login} con mensaje flash si el registro es correcto;
     *       vista {@code registro} con errores de validación o de negocio (usuario duplicado, etc.)</li>
     * </ul>
     */
    @PostMapping("/registro")
    public String registrar(@Valid @ModelAttribute("registroUsuarioForm") RegistroUsuarioForm form,
            BindingResult result,
            RedirectAttributes redirectAttributes) {

        if (!result.hasFieldErrors("password") && !result.hasFieldErrors("confirmPassword")
                && !form.getPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "Las contraseñas no coinciden");
        }

        if (result.hasErrors()) {
            return "registro";
        }

        try {
            usuarioService.registrarUsuario(form);
        } catch (IllegalArgumentException ex) {
            result.rejectValue("username", "registro.error", ex.getMessage());
            return "registro";
        }

        redirectAttributes.addFlashAttribute("registroExitoso", "Usuario registrado correctamente. Ya puedes iniciar sesión.");
        return "redirect:/login";
    }

    /**
     * Página de inicio: delega en el dashboard con la misma paginación de juegos.
     * <ul>
     *   <li><b>URL:</b> {@code GET /}</li>
     *   <li><b>Acceso:</b> público</li>
     *   <li><b>Retorno:</b> vista {@code dashboard} (vía método {@link #dashboard})</li>
     * </ul>
     */
    @GetMapping("/")
    public String inicio(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model) {
        return dashboard(page, size, model);
    }

    /**
     * Dashboard principal con catálogo paginado y carruseles por género.
     * <ul>
     *   <li><b>URL:</b> {@code GET /dashboard}</li>
     *   <li><b>Acceso:</b> público</li>
     *   <li><b>Parámetros:</b> {@code page} (0), {@code size} (12)</li>
     *   <li><b>Retorno:</b> vista {@code dashboard} con {@code juegos} paginados y listas
     *       {@code juegosAccion}, {@code juegosRPG}, {@code juegosShooter},
     *       {@code juegosAventura}, {@code juegosEstrategia} (6 elementos cada una)</li>
     * </ul>
     */
    @GetMapping("/dashboard")
    public String dashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model) {
        model.addAttribute("juegos", juegosService.obtenerTodosPaginados(page, size));
        model.addAttribute("juegosAccion",     juegosService.obtenerPorGenero("Acción", 6));
        model.addAttribute("juegosRPG",        juegosService.obtenerPorGenero("RPG", 6));
        model.addAttribute("juegosShooter",    juegosService.obtenerPorGenero("Shooter", 6));
        model.addAttribute("juegosAventura",   juegosService.obtenerPorGenero("Aventura", 6));
        model.addAttribute("juegosEstrategia", juegosService.obtenerPorGenero("Estrategia", 6));
        return "dashboard";
    }
}
