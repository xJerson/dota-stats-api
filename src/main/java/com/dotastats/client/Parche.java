package com.dotastats.client;

/**
 * Parche del juego tal como lo publica OpenDota en /constants/patch (id incremental y nombre, ej. "7.41").
 */
public record Parche(int id, String nombre) {
}
