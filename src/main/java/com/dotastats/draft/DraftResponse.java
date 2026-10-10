package com.dotastats.draft;

import java.util.List;

/**
 * Resultado de interpretar el draft: qué slots se resolvieron (slot -> nombre completo del héroe),
 * cuáles son ambiguos (slot -> candidatos), cuáles no se encontraron (slot -> texto original),
 * las sugerencias que devuelva el {@link MotorSugerencias} y las advertencias sobre datos que faltaron
 * al calcularlas (vacía si todo se pudo consultar).
 */
public record DraftResponse(
        int miRol,
        Equipos<String> resueltos,
        Equipos<List<String>> ambiguos,
        Equipos<String> noEncontrados,
        List<Sugerencia> sugerencias,
        List<String> advertencias) {
}
