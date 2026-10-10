package com.dotastats.draft;

import com.dotastats.meta.Duelo;

import java.util.Map;

/**
 * Historial de un jugador por héroe.
 *
 * @param porHeroe heroId -> (partidas, victorias) del jugador con ese héroe; solo héroes con al menos una partida
 */
public record RendimientoJugador(Map<Integer, Duelo> porHeroe) {

    public RendimientoJugador {
        porHeroe = Map.copyOf(porHeroe);
    }

    /** @return null si el jugador nunca jugó a ese héroe */
    public Duelo de(int heroId) {
        return porHeroe.get(heroId);
    }
}
