/**
 * Configuración web MVC: recursos estáticos y archivos subidos por usuarios.
 * <p>
 * Expone el directorio configurado en {@code app.upload.dir} bajo la URL pública {@code /uploads/**},
 * de modo que imágenes de juegos u otros ficheros subidos se sirvan desde disco (desarrollo o producción).
 * </p>
 *
 * @author proyecto-aula
 */
package com.proyecto.aula.playgroundv2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implementa {@link WebMvcConfigurer} para registrar manejadores de recursos personalizados.
 * <p>
 * Sirve los archivos subidos desde disco bajo la URL /uploads/**
 * Funciona tanto en desarrollo (src/main/resources/static/uploads)
 * como si se cambia app.upload.dir a una ruta absoluta en producción.
 * </p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Ruta en disco donde se guardan las subidas; por defecto la carpeta static del proyecto.
     */
    @Value("${app.upload.dir:src/main/resources/static/uploads}")
    private String uploadDir;

    /**
     * Mapea {@code /uploads/**} al sistema de ficheros local (prefijo {@code file:}).
     * <p>
     * Cómo funciona: resuelve {@link #uploadDir} a ruta absoluta y la registra como
     * {@code ResourceLocation} para que Spring MVC sirva los bytes sin pasar por un controlador.
     * </p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
