package com.dotastats.analizadores;

import java.util.List;

/**
 * Lo que devuelve {@link ServicioSugerencias}: las filas ya armadas y las advertencias en texto legible
 * recogidas por cualquier analizador (vacía si no hubo ninguna).
 */
public record ResultadoFilas(List<FilaSugerencias> filas, List<String> advertencias) {

    public ResultadoFilas {
        filas = List.copyOf(filas);
        advertencias = List.copyOf(advertencias);
    }
}
