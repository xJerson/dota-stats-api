package com.dotastats.draft;

/**
 * Un héroe sugerido para el pick del usuario, con el motivo en texto legible y el puntaje que lo ordenó.
 */
public record Sugerencia(int heroId, String heroe, String motivo, double puntaje) {
}
