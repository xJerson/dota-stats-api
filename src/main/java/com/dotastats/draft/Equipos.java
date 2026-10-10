package com.dotastats.draft;

import java.util.Map;

/**
 * Agrupa un valor por slot (1..5) para cada equipo del draft.
 */
public record Equipos<T>(Map<Integer, T> enemigos, Map<Integer, T> aliados) {
}
