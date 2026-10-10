package com.dotastats.meta;

import java.util.Map;

/**
 * Contenido del archivo de caché en disco: datos de meta (incluidas las partidas por línea) más los matchups pedidos hasta el momento.
 *
 * @param version  versión del formato; un archivo con otra versión se ignora (la 2 añadió las partidas por línea)
 * @param matchups enemigoId -> (heroId -> duelo), igual que en {@link MatchupsEnemigos}
 */
public record SnapshotMeta(int version, DatosMeta datos, Map<Integer, Map<Integer, Duelo>> matchups) {

    public static final int VERSION = 2;
}
