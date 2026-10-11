package com.dotastats.meta;

import java.util.Set;

/**
 * Estadística de meta de un héroe, ya sumada sobre los brackets configurados.
 *
 * @param nombreInterno clave interna de OpenDota ({@code name} de /heroStats, p. ej. {@code npc_dota_hero_lion});
 *                      null si se desconoce (p. ej. instancias de prueba construidas con el constructor viejo
 *                      de 5 campos). Es la clave que usa {@code /constants/hero_abilities}, así que
 *                      {@code CatalogoHabilidades} la necesita para mapear cada héroe con sus habilidades.
 */
public record HeroeMeta(int id, String nombre, Set<String> roles, long partidas, long victorias, String nombreInterno) {

    public HeroeMeta {
        roles = Set.copyOf(roles);
    }

    /** Sin nombre interno: pruebas existentes y cualquier dato de meta construido antes de esta función. */
    public HeroeMeta(int id, String nombre, Set<String> roles, long partidas, long victorias) {
        this(id, nombre, roles, partidas, victorias, null);
    }

    public double winrate() {
        return partidas == 0 ? 0.0 : (double) victorias / partidas;
    }
}
