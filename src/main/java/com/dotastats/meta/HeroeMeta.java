package com.dotastats.meta;

import java.util.Set;

/**
 * Estadística de meta de un héroe, ya sumada sobre los brackets configurados.
 */
public record HeroeMeta(int id, String nombre, Set<String> roles, long partidas, long victorias) {

    public HeroeMeta {
        roles = Set.copyOf(roles);
    }

    public double winrate() {
        return partidas == 0 ? 0.0 : (double) victorias / partidas;
    }
}
