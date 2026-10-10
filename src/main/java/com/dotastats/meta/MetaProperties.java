package com.dotastats.meta;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Configuración de los datos de meta (prefijo {@code dotastats.meta}).
 *
 * @param brackets          brackets de OpenDota a sumar (1=Herald ... 8=Immortal). Por defecto 6,7 (Ancient y Divine)
 * @param throttleMs        pausa de cortesía entre dos peticiones de matchups consecutivas de un mismo draft;
 *                          0 (por defecto) la desactiva y los enemigos que faltan se piden EN PARALELO (máx. 5 a la
 *                          vez); mayor que 0 fuerza el modo serie con esa pausa
 * @param cargarAlIniciar   si se cargan los datos base (caché de disco, o /heroStats y parche) al arrancar la app
 * @param ttlParcheMinutos  cuánto se reutiliza la consulta del parche vivo antes de volver a pedirla
 * @param cacheFile         archivo JSON donde se persiste el snapshot; vacío desactiva la caché en disco
 */
@ConfigurationProperties("dotastats.meta")
public record MetaProperties(@DefaultValue("6,7") List<Integer> brackets,
                             @DefaultValue("0") long throttleMs,
                             @DefaultValue("true") boolean cargarAlIniciar,
                             @DefaultValue("30") long ttlParcheMinutos,
                             @DefaultValue("./data/meta-cache.json") String cacheFile) {
}
