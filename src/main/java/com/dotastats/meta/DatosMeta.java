package com.dotastats.meta;

import com.dotastats.client.Parche;

import java.time.Instant;
import java.util.Map;

/**
 * Snapshot inmutable de las estadísticas de meta (/heroStats + parche). Se construye entero y se publica de
 * una vez (referencia volatile en {@link DatosMetaService}), así que los lectores nunca ven una carga a medias.
 * Los matchups no forman parte de él: se piden de forma perezosa por enemigo y viven en el servicio.
 *
 * @param heroes heroId -> estadística de meta
 */
public record DatosMeta(Map<Integer, HeroeMeta> heroes, Parche parche, Instant cargadoEn) {

    public DatosMeta {
        heroes = Map.copyOf(heroes);
    }
}
