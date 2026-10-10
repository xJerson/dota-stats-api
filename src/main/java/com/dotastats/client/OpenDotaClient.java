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

    /** Último parche publicado por OpenDota (el de id más alto). */
    Parche obtenerParche();
}
