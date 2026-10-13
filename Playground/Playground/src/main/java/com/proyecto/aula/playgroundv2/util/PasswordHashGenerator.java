/**
 * Utilidad de línea de comandos para generar hashes BCrypt de contraseñas.
 * <p>
 * No forma parte del arranque de Spring Boot: se ejecuta con {@code main} para
 * preparar contraseñas de prueba, seeds de datos o depuración de seguridad.
 * En producción el hash debe generarse siempre en el servicio de registro, no con esta clase.
 * </p>
 *
 * <p>Ejemplo sin argumentos: imprime el hash de {@code "123456"}.</p>
 * <p>Ejemplo con argumentos: imprime un hash por cada contraseña pasada en {@code args}.</p>
 */
package com.proyecto.aula.playgroundv2.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Generador manual de hashes BCrypt vía consola.
 */
public class PasswordHashGenerator {

    /**
     * Punto de entrada: codifica contraseñas y escribe el resultado en stdout.
     * <ul>
     *   <li>Sin argumentos: codifica la contraseña por defecto {@code "123456"}.</li>
     *   <li>Con argumentos: codifica cada cadena recibida y muestra {@code texto => hash}.</li>
     * </ul>
     *
     * @param args contraseñas en texto plano a hashear (opcional)
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println(new BCryptPasswordEncoder().encode("123456"));
            return;
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        for (String password : args) {
            System.out.println(password + " => " + encoder.encode(password));
        }
    }
}
