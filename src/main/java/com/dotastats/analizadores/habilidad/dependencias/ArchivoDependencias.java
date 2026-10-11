package com.dotastats.analizadores.habilidad.dependencias;

import java.util.List;

/** Forma del archivo {@code dependencias-heroes.json}. */
public record ArchivoDependencias(List<DependenciasHeroe> dependencias, List<HerramientasHeroe> herramientas) {

    public ArchivoDependencias {
        dependencias = dependencias == null ? List.of() : List.copyOf(dependencias);
        herramientas = herramientas == null ? List.of() : List.copyOf(herramientas);
    }
}
