package com.dotastats.analizadores.habilidad.dependencias;

/**
 * Una entrada ya calculada de la tabla de contrarrestos: el candidato {@code candidatoId} ataca el
 * {@code mecanismo} del que depende el enemigo {@code enemigoId}.
 *
 * @param habilidad clave interna de la habilidad del candidato (null para RANGO_MAYOR, que sale de las estadísticas)
 * @param motivo    texto en español sin el prefijo "Posible:" (lo añade la fila); si es ultimate ya trae "(desde nivel 6)" dentro de la oración
 * @param confianza "alta", "media" o "baja"
 * @param esUlti    true si la habilidad del candidato es su ultimate
 * @param peso      puntos que aporta: peso de la confianza x multiplicador efecto/mecanismo x fiabilidad
 */
public record Contrarresto(int enemigoId, int candidatoId, Mecanismo mecanismo, Efecto efecto, String habilidad,
                           String motivo,
                           String confianza, boolean esUlti, double peso) {
}
