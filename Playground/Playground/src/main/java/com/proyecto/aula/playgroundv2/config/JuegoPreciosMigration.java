/**
 * Migración de datos al arranque: normaliza el campo {@code precios} de la colección {@code juegos}.
 * <p>
 * Convierte documentos que aún guardan precios como mapa BSON (clave = plataforma, valor = número)
 * al formato actual: lista de subdocumentos {@code { plataforma, precio }}.
 * También elimina el campo legado {@code plataformasVenta} si existía.
 * </p>
 *
 * @author proyecto-aula
 */
package com.proyecto.aula.playgroundv2.config;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * {@link CommandLineRunner} de migración one-shot en cada inicio de la aplicación.
 * <p>
 * Convierte documentos con precios en mapa (formato antiguo) a lista de { plataforma, precio }.
 * </p>
 */
@Configuration
public class JuegoPreciosMigration {

    /**
     * Recorre todos los juegos y actualiza aquellos cuyo {@code precios} sigue siendo un {@link Document} mapa.
     * <p>
     * Cómo funciona: {@code findAll} como {@link Document} crudo; si {@code precios} es un documento
     * sin estructura de lista, itera claves (nombres de plataforma), arma la lista nueva y hace
     * {@code updateFirst} con {@code set precios} y {@code unset plataformasVenta}.
     * </p>
     */
    @Bean
    public CommandLineRunner migrarPreciosJuegos(MongoTemplate mongoTemplate) {
        return args -> {
            List<Document> juegos = mongoTemplate.findAll(Document.class, "juegos");
            for (Document juego : juegos) {
                Object precios = juego.get("precios");
                if (!(precios instanceof Document preciosMap)) {
                    continue;
                }

                // Transformar mapa { Steam: 19.99, ... } → [ { plataforma, precio }, ... ]
                List<Document> lista = new ArrayList<>();
                for (String plataforma : preciosMap.keySet()) {
                    Object valor = preciosMap.get(plataforma);
                    double precio = valor instanceof Number n ? n.doubleValue() : 0.0;
                    lista.add(new Document("plataforma", plataforma).append("precio", precio));
                }

                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(juego.get("_id"))),
                        new Update().set("precios", lista).unset("plataformasVenta"),
                        "juegos");
            }
        };
    }
}
