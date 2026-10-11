package com.dotastats.analizadores.habilidad;

/**
 * Capacidad detectada automáticamente en una habilidad del CANDIDATO (ver {@link DetectorHabilidades}), a
 * partir de {@code /constants/abilities}. Es la mitad "qué puedo hacerle al rival" de una interacción
 * automática; la otra mitad es un {@link Rasgo} del enemigo (ver {@link TablaInteraccionesAutomaticas}).
 */
public enum Capacidad {
    /** Transforma a un enemigo en una bestia inofensiva (Hex). */
    HEX,
    /** Silencia a un enemigo. */
    SILENCIO,
    /** Aturde (stun) a un enemigo. */
    STUN,
    /** Inmoviliza (root, sin impedir atacar ni lanzar hechizos) a un enemigo. */
    ROOT,
    /** Da visión verdadera (true sight), revela invisibles. */
    TRUE_SIGHT,
    /** Daño en área sobre enemigos (sirve para limpiar ilusiones de un golpe). */
    AREA,
    /** Dispel sobre un aliado (incluido uno mismo): quita un disable dispelable. */
    DISPEL_ALIADO
}
