package com.dotastats.analizadores.habilidad.dependencias;

import java.util.List;

/** Las dependencias curadas de un héroe enemigo; {@code heroe} es el nombre canónico de /heroStats. */
public record DependenciasHeroe(String heroe, List<Dependencia> dependencias) {

    public DependenciasHeroe {
        dependencias = dependencias == null ? List.of() : List.copyOf(dependencias);
    }
}
