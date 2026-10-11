package com.dotastats.analizadores.habilidad;

import java.util.List;
import java.util.Map;

/**
 * Tabla de interacciones automáticas: qué {@link Capacidad} del candidato castiga qué {@link Rasgo} del
 * enemigo, y con qué peso RELATIVO (multiplicador sobre el peso base de las interacciones automáticas, ver
 * {@code HabilidadProperties.Automatico}). Es la única tabla de verdad para el motor automático; ajustar un
 * peso o sumar una interacción nueva es tocar solo este archivo.
 * <p>
 * Los multiplicadores son juicio de diseño (no vienen de datos): un hex o un true sight anulan del todo el
 * rasgo que contrarrestan (1.0), mientras que un stun/root/silencio son una respuesta más parcial contra un
 * escape (el enemigo puede seguir escapando apenas termine el disable) y valen menos.
 */
public final class TablaInteraccionesAutomaticas {

    private TablaInteraccionesAutomaticas() {
    }

    private record Entrada(Capacidad capacidad, double multiplicador) {
    }

    private static final Map<Rasgo, List<Entrada>> TABLA = Map.of(
            Rasgo.ESCAPE, List.of(
                    new Entrada(Capacidad.HEX, 1.0),
                    new Entrada(Capacidad.SILENCIO, 0.7),
                    new Entrada(Capacidad.STUN, 0.6),
                    new Entrada(Capacidad.ROOT, 0.6)),
            Rasgo.INVISIBLE, List.of(
                    new Entrada(Capacidad.TRUE_SIGHT, 1.0)),
            Rasgo.ILUSIONES, List.of(
                    new Entrada(Capacidad.AREA, 1.0)),
            Rasgo.CANALIZA, List.of(
                    new Entrada(Capacidad.STUN, 1.0),
                    new Entrada(Capacidad.HEX, 1.0),
                    new Entrada(Capacidad.SILENCIO, 1.0)),
            Rasgo.DISABLE_DISPELABLE, List.of(
                    new Entrada(Capacidad.DISPEL_ALIADO, 1.0)));

    /** Multiplicador (&gt;= 0) de la interacción {@code rasgo <- capacidad}; 0 si no hay ninguna definida. */
    public static double multiplicador(Rasgo rasgo, Capacidad capacidad) {
        return TABLA.getOrDefault(rasgo, List.of()).stream()
                .filter(e -> e.capacidad() == capacidad)
                .mapToDouble(Entrada::multiplicador)
                .findFirst()
                .orElse(0.0);
    }
}
