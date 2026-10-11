package com.dotastats.draft;

/**
 * Un héroe sugerido para el pick del usuario, con el motivo en texto legible y el puntaje que lo ordenó.
 *
 * @param confianza "alta"/"media"/"baja" cuando la fila la asigna (por ejemplo la de habilidad, con la confianza
 *                  más alta entre las reglas que coincidieron); null en la fila de meta, que no tiene ese concepto
 */
public record Sugerencia(int heroId, String heroe, String motivo, double puntaje, String confianza) {

    public Sugerencia(int heroId, String heroe, String motivo, double puntaje) {
        this(heroId, heroe, motivo, puntaje, null);
    }
}
