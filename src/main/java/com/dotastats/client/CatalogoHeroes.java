package com.dotastats.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unifica el catálogo /heroes con los nombres actuales de /heroStats. */
final class CatalogoHeroes {

    private CatalogoHeroes() {
    }

    /**
     * Copia del catálogo donde {@code localized_name} es el de {@code heroStats} para el mismo {@code id}. Un héroe que
     * no aparece en heroStats (o sin nombre) conserva el del catálogo. No modifica las listas recibidas.
     */
    static List<Map<String, Object>> conNombresActuales(List<Map<String, Object>> catalogo,
                                                         List<Map<String, Object>> heroStats) {
        Map<Integer, String> actuales = new HashMap<>();
        if (heroStats != null) {
            for (Map<String, Object> s : heroStats) {
                if (s.get("id") instanceof Number id && s.get("localized_name") instanceof String nombre
                        && !nombre.isBlank()) {
                    actuales.put(id.intValue(), nombre);
                }
            }
        }
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map<String, Object> h : catalogo) {
            Map<String, Object> copia = new HashMap<>(h);
            if (h.get("id") instanceof Number id && actuales.containsKey(id.intValue())) {
                copia.put("localized_name", actuales.get(id.intValue()));
            }
            resultado.add(copia);
        }
        return List.copyOf(resultado);
    }
}
