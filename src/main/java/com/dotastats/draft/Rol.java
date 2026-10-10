package com.dotastats.draft;

import java.util.List;

/**
 * Rol de Dota 2 numerado según la convención de posiciones (1 a 5).
 */
public record Rol(int numero, String nombre) {

    public static final List<Rol> TODOS = List.of(
            new Rol(1, "Carry (hard carry)"),
            new Rol(2, "Mid"),
            new Rol(3, "Offlane"),
            new Rol(4, "Soft support"),
            new Rol(5, "Hard support"));
}
