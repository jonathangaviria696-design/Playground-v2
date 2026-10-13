package com.proyecto.aula.playgroundv2.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.proyecto.aula.playgroundv2.dto.CambioContrasenaForm;
import com.proyecto.aula.playgroundv2.dto.PerfilUsuarioForm;
import com.proyecto.aula.playgroundv2.model.Usuario;
import com.proyecto.aula.playgroundv2.service.UsuarioService;

import jakarta.validation.Valid;

/**
 * Controlador MVC de gestión del perfil del usuario autenticado.
 */
@Controller
@RequestMapping("/perfil")
public class PerfilController {

    private final UsuarioService usuarioService;

    public PerfilController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String mostrarPerfil(Model model, Authentication authentication) {
        cargarModeloPerfil(model, authentication.getName());
        return "perfil";
    }

    @PostMapping
    public String actualizarPerfil(
            @Valid @ModelAttribute("perfilForm") PerfilUsuarioForm form,
            BindingResult result,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            cargarModeloPerfil(model, authentication.getName());
            return "perfil";
        }

        try {
            usuarioService.actualizarPerfil(authentication.getName(), form);
        } catch (IllegalArgumentException ex) {
            result.rejectValue("email", "perfil.error", ex.getMessage());
            cargarModeloPerfil(model, authentication.getName());
            return "perfil";
        }

        redirectAttributes.addFlashAttribute("mensajeExito", "Perfil actualizado correctamente");
        return "redirect:/perfil";
    }

    @PostMapping("/cambiar-contrasena")
    public String cambiarContrasena(
            @Valid @ModelAttribute("contrasenaForm") CambioContrasenaForm form,
            BindingResult result,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (!result.hasFieldErrors("contraseniaNueva") && !result.hasFieldErrors("confirmarContrasenia")
                && !form.getContraseniaNueva().equals(form.getConfirmarContrasenia())) {
            result.rejectValue("confirmarContrasenia", "password.mismatch", "Las contraseñas no coinciden");
        }

        if (result.hasErrors()) {
            cargarModeloPerfil(model, authentication.getName());
            model.addAttribute("mostrarContrasena", true);
            return "perfil";
        }

        try {
            usuarioService.cambiarContrasena(authentication.getName(), form);
        } catch (IllegalArgumentException ex) {
            result.rejectValue("contraseniaActual", "contrasena.error", ex.getMessage());
            cargarModeloPerfil(model, authentication.getName());
            model.addAttribute("mostrarContrasena", true);
            return "perfil";
        }

        redirectAttributes.addFlashAttribute("mensajeExito", "Contraseña actualizada correctamente");
        return "redirect:/perfil";
    }

    private void cargarModeloPerfil(Model model, String username) {
        Usuario usuario = usuarioService.obtenerPorUsername(username);

        if (!model.containsAttribute("perfilForm")) {
            model.addAttribute("perfilForm", usuarioService.toPerfilForm(usuario));
        }
        if (!model.containsAttribute("contrasenaForm")) {
            model.addAttribute("contrasenaForm", new CambioContrasenaForm());
        }

        model.addAttribute("usuario", usuario);
    }
}
