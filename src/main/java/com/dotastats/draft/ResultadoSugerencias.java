package com.dotastats.draft;

import java.util.List;

/**
 * Lo que devuelve un {@link MotorSugerencias}: las sugerencias ordenadas y las advertencias en texto legible
 * sobre datos que faltaron al calcularlas (por ejemplo, un enemigo cuyos matchups no se pudieron consultar).
 */
public record ResultadoSugerencias(List<Sugerencia> sugerencias, List<String> advertencias) {

    public ResultadoSugerencias {
        sugerencias = List.copyOf(sugerencias);
        advertencias = List.copyOf(advertencias);
    }
}
