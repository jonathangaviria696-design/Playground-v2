/**
 * Entidad de usuario registrado en la aplicación.
 * <p>
 * Se almacena en la colección MongoDB {@code usuario}. Los campos {@code email},
 * {@code telefono} y {@code username} tienen índices únicos para login y registro.
 * La contraseña debe guardarse ya hasheada (BCrypt) en capa de servicio, nunca en texto plano.
 * </p>
 *
 * @see com.proyecto.aula.playgroundv2.repository.UsuarioRepository
 * @see com.proyecto.aula.playgroundv2.dto.RegistroUsuarioForm
 */
package com.proyecto.aula.playgroundv2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.*;

/**
 * Documento de usuario: autenticación, autorización por {@link #role} y perfil básico.
 */
@Document(collection = "usuario")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    /** Identificador MongoDB del usuario. */
    @Id
    private String id;

    /**
     * Correo electrónico; único cuando está presente.
     * {@code sparse=true}: solo indexa documentos con valor no nulo, evitando conflictos
     * por múltiples registros sin email.
     */
    @Indexed(unique = true, sparse = true)
    private String email;

    /**
     * Teléfono de contacto; índice único disperso (opcional en registro).
     */
    @Indexed(unique = true, sparse = true)
    private String telefono;

    /** Nombre de usuario para login; obligatorio y único en todo el sistema. */
    @Indexed(unique = true)
    private String username;

    /** Dirección postal o de envío; campo de perfil opcional. */
    private String direccion;

    /** Hash de contraseña (por ejemplo BCrypt), no la contraseña en claro. */
    private String password;

    /** Rol de Spring Security (por ejemplo USER, ADMIN). */
    private String role;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
