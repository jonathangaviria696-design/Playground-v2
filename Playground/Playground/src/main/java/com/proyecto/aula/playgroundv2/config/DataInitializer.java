/**
 * Inicialización de datos al arrancar la aplicación.
 * <p>
 * Garantiza la existencia de un usuario administrador por defecto si la base de datos
 * aún no contiene el username {@code Admin}, facilitando pruebas y primer despliegue.
 * </p>
 *
 * @author proyecto-aula
 */
package com.proyecto.aula.playgroundv2.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.proyecto.aula.playgroundv2.model.Usuario;
import com.proyecto.aula.playgroundv2.repository.UsuarioRepository;

/**
 * Beans {@link CommandLineRunner} que se ejecutan una vez tras levantar el contexto Spring.
 */
@Configuration
public class DataInitializer {

    /**
     * Crea el usuario admin inicial si no existe.
     * <p>
     * Cómo funciona: al iniciar la app, comprueba {@code findByUsername("Admin")};
     * si está vacío, persiste usuario con contraseña BCrypt de {@code admin} y rol {@code ADMIN}.
     * </p>
     */
    @Bean
    public CommandLineRunner initAdminUser(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (usuarioRepository.findByUsername("Admin").isEmpty()) {
                Usuario admin = new Usuario();
                admin.setUsername("Admin");
                admin.setEmail("admin@pg.com");
                admin.setPassword(passwordEncoder.encode("admin"));
                admin.setRole("ADMIN");
                usuarioRepository.save(admin);
            }
        };
    }
}
