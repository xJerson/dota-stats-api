package com.dotastats.analizadores.habilidad;

import java.util.List;

/**
 * Resultado de validar una regla contra {@code /constants/abilities}: si la clave de {@code habilidad} y las de
 * {@code anula} siguen existiendo en el parche vigente.
 */
public record ResultadoValidacionRegla(String reglaId, boolean ok, List<String> problemas) {

    public ResultadoValidacionRegla {
        problemas = List.copyOf(problemas);
    }
}
