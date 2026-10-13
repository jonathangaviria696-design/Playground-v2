/**
 * Objeto de transferencia (DTO) con la respuesta tras un login exitoso.
 * <p>
 * Expone el token JWT (u otro bearer) y el tipo de esquema HTTP para que el cliente
 * lo envíe en cabecera {@code Authorization: Bearer &lt;token&gt;}.
 * </p>
 */
package com.proyecto.aula.playgroundv2.dto;

/**
 * Respuesta estándar de autenticación con token Bearer.
 */
public class LoginResponse {

    /** Esquema de autorización HTTP; por defecto {@code Bearer} (OAuth2/JWT habitual). */
    private String tokenType = "Bearer";

    /** Token de acceso generado tras validar credenciales. */
    private String token;

    /**
     * Construye la respuesta con el token recién emitido.
     *
     * @param token cadena del JWT o token de sesión
     */
    public LoginResponse(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getToken() {
        return token;
    }
}

