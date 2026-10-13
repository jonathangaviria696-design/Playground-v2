/**
 * Capa de servicio para usuarios: registro, consultas y adaptación a Spring Security.
 * <p>
 * Implementa {@link org.springframework.security.core.userdetails.UserDetailsService}
 * para que el login por formulario cargue credenciales y roles desde MongoDB.
 * Las contraseñas se almacenan hasheadas con el {@link org.springframework.security.crypto.password.PasswordEncoder}
 * configurado en la aplicación.
 * </p>
 *
 * @author proyecto-aula
 * @see com.proyecto.aula.playgroundv2.repository.UsuarioRepository
 */
package com.proyecto.aula.playgroundv2.service;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.proyecto.aula.playgroundv2.dto.CambioContrasenaForm;
import com.proyecto.aula.playgroundv2.dto.PerfilUsuarioForm;
import com.proyecto.aula.playgroundv2.dto.RegistroUsuarioForm;
import com.proyecto.aula.playgroundv2.model.Usuario;
import com.proyecto.aula.playgroundv2.repository.UsuarioRepository;

/**
 * Servicio de usuarios: autenticación Spring Security, registro y administración básica.
 */
@Service
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructor con repositorio y codificador de contraseñas.
     */
    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // --- Integración Spring Security ---

    /**
     * Carga un usuario para Spring Security a partir de username o email.
     * <p>
     * Cómo funciona: busca primero por {@code username}; si no existe, por {@code email}.
     * Normaliza el rol al prefijo {@code ROLE_} y construye un {@link UserDetails}
     * estándar de Spring con una sola autoridad.
     * </p>
     *
     * @param username nombre de usuario o correo electrónico
     * @return detalles de usuario para el contexto de seguridad
     * @throws UsernameNotFoundException si no hay coincidencia
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = buscarPorUsernameOEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        String role = normalizarRol(usuario.getRole());
        String password = usuario.getPassword() != null ? usuario.getPassword() : "";

        return new org.springframework.security.core.userdetails.User(
                usuario.getUsername(),
                password,
                List.of(new SimpleGrantedAuthority(role))
        );
    }

    /**
     * Búsqueda flexible: acepta login con username o con email.
     */
    private java.util.Optional<Usuario> buscarPorUsernameOEmail(String identificador) {
        return usuarioRepository.findByUsername(identificador)
                .or(() -> usuarioRepository.findByEmail(identificador));
    }

    /**
     * Asegura que el rol tenga formato {@code ROLE_XXX} exigido por Spring Security.
     *
     * @param role rol almacenado en Mongo (puede venir con o sin prefijo)
     * @return rol normalizado, por defecto {@code ROLE_USER}
     */
    private String normalizarRol(String role) {
        if (role == null || role.isBlank()) {
            return "ROLE_USER";
        }
        role = role.trim().toUpperCase();
        if (role.startsWith("ROLE_")) {
            return role;
        }
        return "ROLE_" + role;
    }

    // --- Registro y utilidades ---

    /**
     * Registra un usuario nuevo desde el formulario web.
     * <p>
     * Valida unicidad del username (consulta por email del formulario en la lógica actual),
     * convierte campos opcionales vacíos a {@code null} para índices únicos sparse en Mongo,
     * hashea la contraseña y asigna rol {@code ROLE_USER}.
     * </p>
     *
     * @param form datos del formulario de registro
     * @throws IllegalArgumentException si el username ya está en uso
     */
    public void registrarUsuario(RegistroUsuarioForm form) {
        if (usuarioRepository.findByEmail(form.getUsername()).isPresent()) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso");
        }

        // Convertir strings vacíos a null para evitar conflictos con índices unique sparse
        String telefono  = blankToNull(form.getTelefono());
        String email     = blankToNull(form.getEmail());
        String direccion = blankToNull(form.getDireccion());

        Usuario usuario = Usuario.builder()
                .username(form.getUsername().trim())
                .email(email)
                .telefono(telefono)
                .direccion(direccion)
                .password(passwordEncoder.encode(form.getPassword()))
                .role("ROLE_USER")
                .build();

        usuarioRepository.save(usuario);
    }

    /**
     * Trata cadenas en blanco como ausencia de valor en BD.
     */
    private String blankToNull(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    /**
     * Obtiene la entidad {@link Usuario} por username o email.
     */
    public Usuario obtenerPorUsername(String username) {
        return buscarPorUsernameOEmail(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + username));
    }

    /** Total de usuarios registrados. */
    public long contarTotal() {
        return usuarioRepository.count();
    }

    /**
     * Busca por id de documento MongoDB.
     */
    public Usuario obtenerPorId(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + id));
    }

    /** Listado completo (administración). */
    public List<Usuario> obtenerTodos() {
        return usuarioRepository.findAll();
    }

    /** Persiste cambios en un usuario existente o nuevo. */
    public void guardarUsuario(Usuario usuario) {
        usuarioRepository.save(usuario);
    }

    /** Elimina un usuario por id. */
    public void eliminarUsuario(String id) {
        usuarioRepository.deleteById(id);
    }

    /**
     * Actualiza los datos de perfil del usuario autenticado.
     *
     * @throws IllegalArgumentException si el email ya pertenece a otro usuario
     */
    public void actualizarPerfil(String username, PerfilUsuarioForm form) {
        Usuario usuario = obtenerPorUsername(username);
        String email = blankToNull(form.getEmail());

        if (email != null && !email.equals(usuario.getEmail())) {
            usuarioRepository.findByEmail(email).ifPresent(existente -> {
                if (!existente.getId().equals(usuario.getId())) {
                    throw new IllegalArgumentException("El email ya está en uso por otro usuario");
                }
            });
        }

        usuario.setEmail(email);
        usuario.setTelefono(blankToNull(form.getTelefono()));
        usuario.setDireccion(blankToNull(form.getDireccion()));
        usuarioRepository.save(usuario);
    }

    /**
     * Cambia la contraseña del usuario autenticado tras validar la actual.
     *
     * @throws IllegalArgumentException si la contraseña actual es incorrecta o las nuevas no coinciden
     */
    public void cambiarContrasena(String username, CambioContrasenaForm form) {
        Usuario usuario = obtenerPorUsername(username);

        if (!passwordEncoder.matches(form.getContraseniaActual(), usuario.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }

        if (!form.getContraseniaNueva().equals(form.getConfirmarContrasenia())) {
            throw new IllegalArgumentException("Las nuevas contraseñas no coinciden");
        }

        usuario.setPassword(passwordEncoder.encode(form.getContraseniaNueva()));
        usuarioRepository.save(usuario);
    }

    /** Convierte una entidad Usuario al formulario de edición de perfil. */
    public PerfilUsuarioForm toPerfilForm(Usuario usuario) {
        PerfilUsuarioForm form = new PerfilUsuarioForm();
        form.setEmail(usuario.getEmail() != null ? usuario.getEmail() : "");
        form.setTelefono(usuario.getTelefono() != null ? usuario.getTelefono() : "");
        form.setDireccion(usuario.getDireccion() != null ? usuario.getDireccion() : "");
        return form;
    }
}
