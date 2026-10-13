/**
 * Objeto de transferencia (DTO) para la petición de inicio de sesión.
 * <p>
 * Se usa en endpoints REST o formularios de login: el cliente envía credenciales
 * que el servicio de autenticación valida contra {@link com.proyecto.aula.playgroundv2.model.Usuario}.
 * Las anotaciones Jakarta Validation rechazan peticiones incompletas antes de llegar al servicio.
 * </p>
 */
package com.proyecto.aula.playgroundv2.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de la solicitud de login: nombre de usuario y contraseña.
 */
public class LoginRequest {

    /** Nombre de usuario registrado en el sistema. */
    @NotBlank(message = "El usuario es obligatorio")
    private String username;

    /** Contraseña en texto plano (solo en tránsito; el servidor la compara con el hash almacenado). */
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
