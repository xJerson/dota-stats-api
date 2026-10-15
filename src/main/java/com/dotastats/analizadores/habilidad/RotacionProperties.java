package com.dotastats.analizadores.habilidad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rotación de soportes en la fila "Sugerencias por jugabilidad" (mismo prefijo {@code dotastats.habilidad}): los
 * soportes (posiciones 4 y 5) rotan a mid en las partidas reales, así que el enemigo de ese slot también cuenta
 * para ellos, con menos peso que un rival directo de su línea.
 *
 * @param slotsRotacion posición (1-5) -> slots enemigos (1-5) a los que rota esa posición; una posición sin entrada
 *                      usa el valor por defecto (4 y 5 -> [2]; 1 a 3 -> ninguno) y una lista vacía apaga la rotación
 *                      de esa posición. Propiedad: {@code dotastats.habilidad.slots-rotacion.N}
 * @param pesoRotacion  multiplicador (>= 0) de los puntos de una coincidencia contra un enemigo de rotación; los
 *                      rivales directos siguen pesando 1,0. 0 apaga la rotación.
 *                      Propiedad: {@code dotastats.habilidad.peso-rotacion}
 */
@ConfigurationProperties("dotastats.habilidad")
public record RotacionProperties(Map<Integer, List<Integer>> slotsRotacion,
                                 @DefaultValue("0.5") double pesoRotacion) {

    private static final Map<Integer, List<Integer>> POR_DEFECTO = Map.of(4, List.of(2), 5, List.of(2));

    public RotacionProperties {
        Map<Integer, List<Integer>> resuelto = new HashMap<>(POR_DEFECTO);
        if (slotsRotacion != null) {
            slotsRotacion.forEach((posicion, slots) -> {
                if (posicion == null || posicion < 1 || posicion > 5) {
                    throw new IllegalArgumentException(
                            "dotastats.habilidad.slots-rotacion: la posición debe ir de 1 a 5, no " + posicion);
                }
                List<Integer> lista = slots == null ? List.<Integer>of() : slots;
                for (Integer slot : lista) {
                    if (slot == null || slot < 1 || slot > 5) {
                        throw new IllegalArgumentException("dotastats.habilidad.slots-rotacion." + posicion
                                + ": cada slot debe ir de 1 a 5, no " + slot);
                    }
                }
                resuelto.put(posicion, List.copyOf(lista));
            });
        }
        if (Double.isNaN(pesoRotacion) || pesoRotacion < 0) {
            throw new IllegalArgumentException(
                    "dotastats.habilidad.peso-rotacion: debe ser mayor o igual a 0, no " + pesoRotacion);
        }
        slotsRotacion = Map.copyOf(resuelto);
    }

    /** Slots enemigos a los que rota la {@code posicion} dada (vacío si no rota). */
    public List<Integer> slotsDe(int posicion) {
        return slotsRotacion.getOrDefault(posicion, List.of());
    }
}
