package com.dotastats.analizadores.habilidad;

/**
 * Rasgo detectado automáticamente en una habilidad ENEMIGA (ver {@link DetectorHabilidades}), a partir de
 * {@code /constants/abilities}. Es la mitad "qué tiene el rival" de una interacción automática; la otra mitad
 * es una {@link Capacidad} del candidato (ver {@link TablaInteraccionesAutomaticas}).
 */
public enum Rasgo {
    /** Escape por habilidad activa (teletransporte, blink, dash, etc.), no por un objeto. */
    ESCAPE,
    /** Se vuelve invisible con alguna habilidad. */
    INVISIBLE,
    /** Crea ilusiones de sí mismo. */
    ILUSIONES,
    /** Tiene una habilidad canalizada (vulnerable a cualquier disable mientras canaliza). */
    CANALIZA,
    /** Depende de un disable (stun/silencio/hex/root) sobre ENEMIGOS que un dispel básico quita. */
    DISABLE_DISPELABLE
}
