package com.dotastats.meta;

import java.util.Map;

/**
 * Datos crudos de OpenDota que necesita {@code CatalogoHabilidades} para detectar interacciones automáticas
 * de habilidad: ver {@link DatosMetaService#datosHabilidad()}.
 *
 * @param abilidades       {@code /constants/abilities} (clave de habilidad -> sus datos)
 * @param habilidadesPorHeroe {@code /constants/hero_abilities} (nombre interno del héroe -> sus habilidades)
 * @param rangoAtaquePorHeroe  {@code attack_range} base de {@code /constants/heroes} (heroId -> alcance); vacío si no
 *                             se pudo obtener (la herramienta derivada RANGO_MAYOR simplemente no aparece)
 */
public record DatosHabilidad(Map<String, Object> abilidades, Map<String, Object> habilidadesPorHeroe,
                             Map<Integer, Integer> rangoAtaquePorHeroe) {

    public DatosHabilidad {
        abilidades = Map.copyOf(abilidades);
        habilidadesPorHeroe = Map.copyOf(habilidadesPorHeroe);
        rangoAtaquePorHeroe = rangoAtaquePorHeroe == null ? Map.of() : Map.copyOf(rangoAtaquePorHeroe);
    }

    /** Sin alcance de ataque. */
    public DatosHabilidad(Map<String, Object> abilidades, Map<String, Object> habilidadesPorHeroe) {
        this(abilidades, habilidadesPorHeroe, Map.of());
    }
}
