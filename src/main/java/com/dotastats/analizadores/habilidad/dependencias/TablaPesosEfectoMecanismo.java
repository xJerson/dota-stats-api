package com.dotastats.analizadores.habilidad.dependencias;

import java.util.EnumMap;
import java.util.Map;

/**
 * Qué {@link Efecto} castiga qué {@link Mecanismo} y con qué peso RELATIVO (multiplicador sobre el peso de la
 * confianza de la herramienta). Es la única tabla de verdad: ajustar un peso o sumar una relación es tocar
 * solo este archivo (mismo estilo que {@code TablaInteraccionesAutomaticas}, con los mismos pesos para el escape).
 * <p>
 * Son juicio de diseño, no datos: un hex desactiva TODAS las habilidades del enemigo (1.0); un silencio le
 * quita las que lanza pero no el movimiento normal (0.7); un stun o un root terminan y el héroe puede seguir
 * escapando después (0.6). Revelar anula del todo la invisibilidad (1.0), mientras que un área solo pega si
 * adivinas dónde está (0.4). Para ilusiones el área es lo que las limpia (1.0); quemar maná castiga la
 * dependencia del maná (1.0); el break anula las pasivas (1.0) y el desarme solo los ataques (0.7); más
 * alcance de ataque quita la ventaja del acoso (0.6).
 */
public final class TablaPesosEfectoMecanismo {

    private TablaPesosEfectoMecanismo() {
    }

    private static final Map<Mecanismo, Map<Efecto, Double>> TABLA = new EnumMap<>(Mecanismo.class);

    static {
        TABLA.put(Mecanismo.ESCAPE, Map.of(
                Efecto.HEX, 1.0,
                Efecto.SILENCIO, 0.7,
                Efecto.STUN, 0.6,
                Efecto.ROOT, 0.6));
        TABLA.put(Mecanismo.INVISIBILIDAD, Map.of(
                Efecto.REVELADO, 1.0,
                Efecto.AREA, 0.4));
        TABLA.put(Mecanismo.ILUSIONES, Map.of(
                Efecto.AREA, 1.0));
        TABLA.put(Mecanismo.MANA, Map.of(
                Efecto.QUEMA_MANA, 1.0));
        TABLA.put(Mecanismo.PASIVAS, Map.of(
                Efecto.BREAK, 1.0,
                Efecto.DESARME, 0.7));
        TABLA.put(Mecanismo.ACOSO_RANGO, Map.of(
                Efecto.RANGO_MAYOR, 0.6));
    }

    /** Multiplicador (&gt;= 0) de {@code efecto} contra {@code mecanismo}; 0 si no hay relación. */
    public static double multiplicador(Mecanismo mecanismo, Efecto efecto) {
        return TABLA.getOrDefault(mecanismo, Map.of()).getOrDefault(efecto, 0.0);
    }
}
