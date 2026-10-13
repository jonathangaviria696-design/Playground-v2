/**
 * Punto de entrada de la aplicación Spring Boot {@code playground}.
 * <p>
 * Esta clase arranca el contenedor de Spring, habilita el escaneo automático de
 * componentes (controladores, servicios, repositorios, configuración de seguridad, etc.)
 * y delega en {@link org.springframework.boot.SpringApplication} el arranque del servidor embebido.
 * </p>
 * <p>
 * Rol en la aplicación: clase raíz del módulo; sin lógica de negocio propia.
 * Todo el comportamiento funcional reside en paquetes {@code model}, {@code dto},
 * {@code repository}, {@code service} y {@code controller}.
 * </p>
 *
 * @author proyecto-aula
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 */
package com.proyecto.aula.playgroundv2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicación principal de Playground (catálogo de juegos, usuarios y wishlist).
 */
@SpringBootApplication
public class PlaygroundApplication {

	/**
	 * Método {@code main} estándar de Java: inicia el contexto de Spring Boot.
	 *
	 * @param args argumentos de línea de comandos (opcionales; no se usan en esta clase)
	 */
	public static void main(String[] args) {
		SpringApplication.run(PlaygroundApplication.class, args);
	}

}
