package com.dotastats.analizadores.habilidad.dependencias;

/**
 * Qué hace una herramienta (una habilidad de un candidato). El efecto se CURA a mano en
 * {@code dependencias-heroes.json}: OpenDota no dice de qué tipo es una habilidad (un stun y un hex usan la
 * misma clave genérica {@code duration}), así que nunca se infiere de la descripción.
 */
public enum Efecto {
    HEX(true),
    SILENCIO(true),
    STUN(true),
    ROOT(true),
    DESARME(true),
    REVELADO(false),
    AREA(false),
    QUEMA_MANA(false),
    BREAK(false),
    /** Derivado de las estadísticas (alcance de ataque base mayor que el del enemigo); no se cura en el archivo. */
    RANGO_MAYOR(false);

    private final boolean exigeDuracion;

    Efecto(boolean exigeDuracion) {
        this.exigeDuracion = exigeDuracion;
    }

    /** true si la herramienta debe indicar la clave del atributo que guarda la duración del efecto. */
    public boolean exigeDuracion() {
        return exigeDuracion;
    }
}
