package com.proyecto.aula.playgroundv2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Formulario para cambiar la contraseña del usuario autenticado.
 */
public class CambioContrasenaForm {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String contraseniaActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 4, message = "La contraseña debe tener al menos 4 caracteres")
    private String contraseniaNueva;

    @NotBlank(message = "La confirmación es obligatoria")
    private String confirmarContrasenia;

    public String getContraseniaActual() { return contraseniaActual; }
    public void setContraseniaActual(String contraseniaActual) { this.contraseniaActual = contraseniaActual; }

    public String getContraseniaNueva() { return contraseniaNueva; }
    public void setContraseniaNueva(String contraseniaNueva) { this.contraseniaNueva = contraseniaNueva; }

    public String getConfirmarContrasenia() { return confirmarContrasenia; }
    public void setConfirmarContrasenia(String confirmarContrasenia) { this.confirmarContrasenia = confirmarContrasenia; }
}
