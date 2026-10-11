package com.dotastats.meta;

import com.dotastats.juego.IndicadoresHabilidad;

import java.util.Map;

/**
 * Datos crudos que necesita {@code CatalogoHabilidades} (y {@code TablaContrarrestos}) para detectar interacciones de
 * habilidad: ver {@link DatosMetaService#datosHabilidad()}.
 *
 * @param abilidades       {@code /constants/abilities} (clave de habilidad -> sus datos)
 * @param habilidadesPorHeroe {@code /constants/hero_abilities} (nombre interno del héroe -> sus habilidades)
 * @param rangoAtaquePorHeroe  {@code attack_range} base de {@code /constants/heroes} (heroId -> alcance); vacío si no
 *                             se pudo obtener (la herramienta derivada RANGO_MAYOR simplemente no aparece)
 * @param indicadoresPorHeroe  indicadores de los archivos del juego (nombre interno del héroe -> clave de habilidad ->
 *                             indicadores); vacío si no estaban disponibles (entonces no hay dependencias ni
 *                             herramientas derivadas y la fila usa las interacciones por palabra clave)
 */
public record DatosHabilidad(Map<String, Object> abilidades, Map<String, Object> habilidadesPorHeroe,
                             Map<Integer, Integer> rangoAtaquePorHeroe,
                             Map<String, Map<String, IndicadoresHabilidad>> indicadoresPorHeroe) {

    public DatosHabilidad {
        abilidades = Map.copyOf(abilidades);
        habilidadesPorHeroe = Map.copyOf(habilidadesPorHeroe);
        rangoAtaquePorHeroe = rangoAtaquePorHeroe == null ? Map.of() : Map.copyOf(rangoAtaquePorHeroe);
        indicadoresPorHeroe = indicadoresPorHeroe == null ? Map.of() : Map.copyOf(indicadoresPorHeroe);
    }

    /** Sin indicadores del juego. */
    public DatosHabilidad(Map<String, Object> abilidades, Map<String, Object> habilidadesPorHeroe,
                          Map<Integer, Integer> rangoAtaquePorHeroe) {
        this(abilidades, habilidadesPorHeroe, rangoAtaquePorHeroe, Map.of());
    }

    /** Sin alcance de ataque ni indicadores del juego. */
    public DatosHabilidad(Map<String, Object> abilidades, Map<String, Object> habilidadesPorHeroe) {
        this(abilidades, habilidadesPorHeroe, Map.of(), Map.of());
    }
}
