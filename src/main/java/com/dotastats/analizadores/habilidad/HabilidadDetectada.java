package com.dotastats.analizadores.habilidad;

/**
 * Una habilidad concreta (de {@code /constants/abilities}) en la que {@link DetectorHabilidades} detectó un
 * {@link Rasgo} o una {@link Capacidad}, con lo necesario para construir el motivo de una interacción
 * automática.
 *
 * @param clave clave interna de OpenDota (p. ej. {@code storm_spirit_ball_lightning})
 * @param nombre nombre mostrado ({@code dname}); si OpenDota no lo trae, se usa {@code clave}
 * @param esUlti si la habilidad es la ULTIMATE del héroe (ver {@link DetectorHabilidades#esUlti})
 */
public record HabilidadDetectada(String clave, String nombre, boolean esUlti) {
}
