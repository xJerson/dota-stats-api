package com.dotastats.draft;

import com.dotastats.analizadores.FilaSugerencias;

import java.util.List;

/**
 * Resultado de interpretar el draft: qué slots se resolvieron (slot -> nombre completo del héroe),
 * cuáles son ambiguos (slot -> candidatos), cuáles no se encontraron (slot -> texto original),
 * las filas de sugerencias (una por {@code com.dotastats.analizadores.AnalizadorDraft}, por ejemplo
 * "Sugerencias por meta" y "Sugerencias por jugabilidad", sin héroes repetidos entre ellas) y las
 * advertencias sobre datos que faltaron al calcularlas (vacía si todo se pudo consultar).
 */
public record DraftResponse(
        int miRol,
        Equipos<String> resueltos,
        Equipos<List<String>> ambiguos,
        Equipos<String> noEncontrados,
        List<FilaSugerencias> filas,
        List<String> advertencias) {
}
