package com.dotastats.meta;

import com.dotastats.client.Parche;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Snapshot inmutable de las estadísticas de meta (/heroStats + parche + partidas por línea). Se construye entero y se
 * publica de una vez (referencia volatile en {@link DatosMetaService}), así que los lectores nunca ven una carga a
 * medias. Los matchups no forman parte de él: se piden de forma perezosa por enemigo y viven en el servicio.
 *
 * @param heroes          heroId -> estadística de meta
 * @param partidasPorLinea lane_role de OpenDota (1 segura, 2 central, 3 ofensiva) -> (heroId -> partidas jugadas en
 *                        esa línea, sumadas todos los tramos de tiempo). Vacío si no se pudieron obtener las tres
 *                        líneas: o están todas o no hay ninguna, para que las participaciones sean comparables.
 */
public record DatosMeta(Map<Integer, HeroeMeta> heroes, Parche parche, Instant cargadoEn,
                        Map<Integer, Map<Integer, Long>> partidasPorLinea) {

    public DatosMeta {
        heroes = Map.copyOf(heroes);
        Map<Integer, Map<Integer, Long>> copia = new HashMap<>();
        if (partidasPorLinea != null) {
            partidasPorLinea.forEach((linea, porHeroe) -> copia.put(linea, Map.copyOf(porHeroe)));
        }
        partidasPorLinea = Map.copyOf(copia);
    }

    /** Datos sin información de líneas. */
    public DatosMeta(Map<Integer, HeroeMeta> heroes, Parche parche, Instant cargadoEn) {
        this(heroes, parche, cargadoEn, Map.of());
    }
}
