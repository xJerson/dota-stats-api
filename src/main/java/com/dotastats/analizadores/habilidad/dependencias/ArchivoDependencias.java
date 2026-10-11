package com.dotastats.analizadores.habilidad.dependencias;

import java.util.List;

/**
 * Forma del archivo {@code dependencias-heroes.json}.
 *
 * @param movimientoNoEscape claves de habilidad (p. ej. {@code spirit_breaker_charge_of_darkness}) que los archivos del
 *                           juego marcan como "no se puede usar enraizado" pero que NO son un escape (acercarse al
 *                           enemigo, autodestrucción...): se excluyen de las dependencias ESCAPE derivadas. Opcional.
 */
public record ArchivoDependencias(List<DependenciasHeroe> dependencias, List<HerramientasHeroe> herramientas,
                                  List<String> movimientoNoEscape) {

    public ArchivoDependencias {
        dependencias = dependencias == null ? List.of() : List.copyOf(dependencias);
        herramientas = herramientas == null ? List.of() : List.copyOf(herramientas);
        movimientoNoEscape = movimientoNoEscape == null ? List.of() : List.copyOf(movimientoNoEscape);
    }
}
