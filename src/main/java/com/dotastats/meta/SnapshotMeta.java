package com.dotastats.meta;

import com.dotastats.juego.IndicadoresHabilidad;

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
 * @param indicadoresPorHeroe indicadores de los archivos del juego (nombre interno del héroe -> clave de habilidad
 *                            -> indicadores); misma política que el alcance de ataque: vacío si todavía no se pidió o
 *                            si el archivo es de antes de que existiera (se lee igual, SIN subir {@link #VERSION}, y
 *                            se piden solo los indicadores)
 */
public record SnapshotMeta(int version, DatosMeta datos, Map<Integer, Map<Integer, Duelo>> matchups,
                           Map<String, Object> abilidades, Map<String, Object> habilidadesPorHeroe,
                           Map<Integer, Integer> rangoAtaquePorHeroe,
                           Map<String, Map<String, IndicadoresHabilidad>> indicadoresPorHeroe) {

    public static final int VERSION = 4;

    public SnapshotMeta {
        abilidades = abilidades == null ? Map.of() : Map.copyOf(abilidades);
        habilidadesPorHeroe = habilidadesPorHeroe == null ? Map.of() : Map.copyOf(habilidadesPorHeroe);
        rangoAtaquePorHeroe = rangoAtaquePorHeroe == null ? Map.of() : Map.copyOf(rangoAtaquePorHeroe);
        indicadoresPorHeroe = indicadoresPorHeroe == null ? Map.of() : Map.copyOf(indicadoresPorHeroe);
    }

    /** Sin indicadores del juego: pruebas existentes y cachés anteriores a esta función. */
    public SnapshotMeta(int version, DatosMeta datos, Map<Integer, Map<Integer, Duelo>> matchups,
                        Map<String, Object> abilidades, Map<String, Object> habilidadesPorHeroe,
                        Map<Integer, Integer> rangoAtaquePorHeroe) {
        this(version, datos, matchups, abilidades, habilidadesPorHeroe, rangoAtaquePorHeroe, Map.of());
    }

    /** Sin alcance de ataque ni indicadores: pruebas existentes y cachés anteriores a estas funciones. */
    public SnapshotMeta(int version, DatosMeta datos, Map<Integer, Map<Integer, Duelo>> matchups,
                        Map<String, Object> abilidades, Map<String, Object> habilidadesPorHeroe) {
        this(version, datos, matchups, abilidades, habilidadesPorHeroe, Map.of(), Map.of());
    }
}
