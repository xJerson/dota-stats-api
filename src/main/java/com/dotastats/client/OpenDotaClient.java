package com.dotastats.client;

import java.util.List;
import java.util.Map;

/**
 * Interfaz hacia la API de OpenDota. Se define como interfaz (y no como clase concreta)
 * para poder mockearla en los tests de servicio sin hacer llamadas de red reales.
 */
public interface OpenDotaClient {

    Object obtenerRecurso(long accountId, String subruta, Map<String, String> parametros);

    Object refrescar(long accountId);

    List<Map<String, Object>> obtenerCatalogoHeroes();

    /** /heroStats: un elemento por héroe con id, localized_name, roles y contadores {n}_pick / {n}_win por bracket. */
    List<Map<String, Object>> obtenerHeroStats();

    /** /heroes/{id}/matchups: elementos {hero_id, games_played, wins}; wins son victorias de {@code heroId} contra hero_id. */
    List<Map<String, Object>> obtenerMatchups(int heroId);

    /**
     * /players/{id}/heroes: un elemento por héroe con hero_id, games y win (partidas y victorias del jugador).
     * Una cuenta sin datos públicos devuelve igualmente todas las filas, con games = 0.
     */
    List<Map<String, Object>> obtenerHeroesJugador(long accountId);

    /**
     * /scenarios/laneRoles?lane_role={n} (1 segura, 2 central, 3 ofensiva, 4 jungla): varias filas por héroe, una por
     * tramo de tiempo, con hero_id, lane_role, time, y games / wins como CADENAS ("21"), no como números.
     */
    List<Map<String, Object>> obtenerLaneRoles(int laneRole);

    /** Último parche publicado por OpenDota (el de id más alto). */
    Parche obtenerParche();

    /** /constants/abilities: clave interna de habilidad -> sus datos (nombre, comportamiento, dispel, etc.). */
    Map<String, Object> obtenerAbilidades();

    /**
     * /constants/hero_abilities: nombre interno del héroe ({@code npc_dota_hero_x}) -> sus habilidades (entre
     * otros campos, {@code abilities}: la lista de claves internas, en orden, incluida {@code generic_hidden}
     * y las de talento {@code special_bonus_*} que no son habilidades jugables).
     */
    Map<String, Object> obtenerHabilidadesPorHeroe();

    /**
     * /constants/heroes: id del héroe (como cadena) -> sus datos base (entre otros, {@code attack_range}, el alcance
     * de ataque base). Se usa para la herramienta derivada RANGO_MAYOR de la fila de jugabilidad.
     */
    Map<String, Object> obtenerHeroesConstantes();
}
