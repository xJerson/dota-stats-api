package com.dotastats.analizadores.habilidad.dependencias;

import java.util.List;

/** Las herramientas curadas de un candidato; {@code heroe} es el nombre canónico de /heroStats. */
public record HerramientasHeroe(String heroe, List<Herramienta> herramientas) {

    public HerramientasHeroe {
        herramientas = herramientas == null ? List.of() : List.copyOf(herramientas);
    }
}
