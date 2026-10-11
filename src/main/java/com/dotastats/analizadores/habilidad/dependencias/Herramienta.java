package com.dotastats.analizadores.habilidad.dependencias;

/**
 * Una habilidad de un candidato que ataca una dependencia. Los números NO se curan: salen de los atributos de
 * OpenDota a nivel 1 usando las claves que aquí se indican, y se validan contra el snapshot al construir la tabla.
 *
 * @param habilidad      clave interna de OpenDota (p. ej. {@code lion_voodoo})
 * @param efecto         qué hace (curado a mano)
 * @param claveDuracion  clave del atributo con la duración del efecto (p. ej. {@code silence_duration}); obligatoria
 *                       para HEX, SILENCIO, STUN, ROOT y DESARME
 * @param claveEvidencia clave de un atributo que respalda el efecto cuando no hay duración (p. ej.
 *                       {@code true_sight_radius}, {@code radius}, {@code mana_per_hit}); su valor a nivel 1 debe ser &gt; 0
 * @param claveRetraso   clave opcional del atributo con el retraso (s) entre el lanzamiento y el efecto
 * @param esUlti         true si es la ultimate del héroe (solo existe desde el nivel 6)
 * @param confianza      "alta", "media" o "baja"; las de "media"/"baja" salen como "Posible: ..."
 */
public record Herramienta(String habilidad, Efecto efecto, String claveDuracion, String claveEvidencia,
                          String claveRetraso, boolean esUlti, String confianza) {
}
