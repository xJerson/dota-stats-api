package com.dotastats.analizadores.habilidad.dependencias;

/**
 * Un mecanismo del que depende un héroe enemigo.
 *
 * @param mecanismo   de qué depende
 * @param habilidad   clave interna de OpenDota de la habilidad en la que se apoya (p. ej.
 *                    {@code storm_spirit_ball_lightning}); null cuando depende de una estadística (el alcance)
 * @param descripcion explicación en español de por qué depende de eso
 */
public record Dependencia(Mecanismo mecanismo, String habilidad, String descripcion) {
}
