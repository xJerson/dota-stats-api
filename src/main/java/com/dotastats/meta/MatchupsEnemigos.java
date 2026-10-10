package com.dotastats.meta;

import java.util.Map;
import java.util.Set;

/**
 * Matchups disponibles para los enemigos de un draft.
 *
 * @param porEnemigo enemigoId -> (heroId -> duelo); como en OpenDota, las victorias son del enemigo
 * @param fallidos   enemigos cuyos matchups no se pudieron obtener (OpenDota caído, límite, timeout)
 */
public record MatchupsEnemigos(Map<Integer, Map<Integer, Duelo>> porEnemigo, Set<Integer> fallidos) {

    public MatchupsEnemigos {
        porEnemigo = Map.copyOf(porEnemigo);
        fallidos = Set.copyOf(fallidos);
    }

    /**
     * Duelo de {@code candidatoId} contra {@code enemigoId} desde la perspectiva del candidato, derivado del
     * complemento de lo que OpenDota guarda para el enemigo.
     *
     * @return null si no hay datos de ese enfrentamiento
     */
    public Duelo duelo(int candidatoId, int enemigoId) {
        Map<Integer, Duelo> delEnemigo = porEnemigo.get(enemigoId);
        if (delEnemigo == null) {
            return null;
        }
        Duelo d = delEnemigo.get(candidatoId);
        return d == null ? null : d.complemento();
    }
}
