package com.dotastats.analizadores.habilidad.dependencias;

/**
 * De qué DEPENDE un héroe enemigo para funcionar en línea ("contrarrestar la dependencia, no al héroe").
 * Cada héroe curado en {@code dependencias-heroes.json} declara entre 1 y 3.
 */
public enum Mecanismo {
    /** Se escapa o se reposiciona con una habilidad activa (p. ej. Ball Lightning). */
    ESCAPE("escape"),
    /** Necesita ser invisible para jugar (p. ej. Riki). */
    INVISIBILIDAD("invisibilidad"),
    /** Su daño o su presión salen de ilusiones (p. ej. Phantom Lancer). */
    ILUSIONES("ilusiones"),
    /** Se apoya en habilidades pasivas o en modificadores de ataque (p. ej. Huskar). */
    PASIVAS("pasivas"),
    /** Acosa a distancia con su alcance de ataque (p. ej. Viper). */
    ACOSO_RANGO("acoso a distancia"),
    /** Sus habilidades consumen mucho maná (p. ej. Storm Spirit al moverse). */
    MANA("maná");

    private final String texto;

    Mecanismo(String texto) {
        this.texto = texto;
    }

    /** Nombre en español, tal como aparece en el motivo. */
    public String texto() {
        return texto;
    }
}
