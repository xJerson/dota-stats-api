package com.dotastats.analizadores;

import com.dotastats.draft.Sugerencia;

import java.util.List;

/**
 * Una fila de la respuesta del draft: el título que ve el usuario y sus sugerencias, ya deduplicadas contra
 * las filas anteriores y recortadas al máximo configurado.
 */
public record FilaSugerencias(String id, String titulo, List<Sugerencia> sugerencias) {

    public FilaSugerencias {
        sugerencias = List.copyOf(sugerencias);
    }
}
