package com.dotastats.meta;

import java.util.Map;

/**
 * Contenido del archivo de caché en disco: datos de meta (incluidas las partidas por línea) más los matchups pedidos hasta el momento.
 *
 * @param version  versión del formato; un archivo con otra versión se ignora (la 2 añadió las partidas por línea,
 *                 la 3 añadió las victorias por línea, la 4 añadió el nombre interno de cada héroe y las
 *                 habilidades crudas de OpenDota)
 * @param matchups enemigoId -> (heroId -> duelo), igual que en {@link MatchupsEnemigos}
 * @param abilidades       {@code /constants/abilities} cruda (clave de habilidad -> sus datos), tal como la
 *                         pide {@code CatalogoHabilidades} por {@code DatosMetaService#datosHabilidad()}; vacía
 *                         si todavía no se pidió en esta sesión
 * @param habilidadesPorHeroe {@code /constants/hero_abilities} cruda (nombre interno del héroe -> sus
 *                             habilidades); vacía en las mismas condiciones que {@code abilidades}
 * @param rangoAtaquePorHeroe {@code attack_range} base de {@code /constants/heroes} (heroId -> alcance); vacío si
 *                            todavía no se pidió o si el archivo es de antes de que existiera (se lee igual y se
 *                            pide solo ese dato, sin invalidar el resto de la caché)
 */
public record SnapshotMeta(int version, DatosMeta datos, Map<Integer, Map<Integer, Duelo>> matchups,
                           Map<String, Object> abilidades, Map<String, Object> habilidadesPorHeroe,
                           Map<Integer, Integer> rangoAtaquePorHeroe) {

    public static final int VERSION = 4;

    public SnapshotMeta {
        abilidades = abilidades == null ? Map.of() : Map.copyOf(abilidades);
        habilidadesPorHeroe = habilidadesPorHeroe == null ? Map.of() : Map.copyOf(habilidadesPorHeroe);
        rangoAtaquePorHeroe = rangoAtaquePorHeroe == null ? Map.of() : Map.copyOf(rangoAtaquePorHeroe);
    }

    /** Sin alcance de ataque: pruebas existentes y cachés anteriores a esta función. */
    public SnapshotMeta(int version, DatosMeta datos, Map<Integer, Map<Integer, Duelo>> matchups,
                        Map<String, Object> abilidades, Map<String, Object> habilidadesPorHeroe) {
        this(version, datos, matchups, abilidades, habilidadesPorHeroe, Map.of());
    }
}
