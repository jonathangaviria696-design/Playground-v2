/**
 * Formulario/DTO de alta de nuevo usuario.
 * <p>
 * Recoge datos del registro web o API antes de mapearlos a {@link com.proyecto.aula.playgroundv2.model.Usuario}.
 * Incluye validación de campos obligatorios, formato de email y longitud mínima de contraseña.
 * La confirmación de contraseña se valida típicamente en el servicio comparando ambos campos.
 * </p>
 */
package com.proyecto.aula.playgroundv2.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos enviados por el cliente al registrarse; no incluye el id generado por MongoDB.
 */
public class RegistroUsuarioForm {

    @NotBlank(message = "El usuario es obligatorio")
    private String username;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no es válido")
    private String email;

    /** Teléfono opcional; sin {@code @NotBlank}. */
    private String telefono;

    /** Dirección opcional; sin {@code @NotBlank}. */
    private String direccion;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 4, message = "La contraseña debe tener al menos 4 caracteres")
    private String password;

    /** Debe coincidir con {@link #password}; validación de igualdad en capa de servicio. */
    @NotBlank(message = "La confirmación es obligatoria")
    private String confirmPassword;

    /**
     * Rol solicitado o asignado (por ejemplo USER); puede restringirse en servidor
     * para impedir auto-registro como ADMIN.
     */
    private String role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
