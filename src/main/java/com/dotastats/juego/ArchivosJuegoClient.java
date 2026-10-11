package com.dotastats.juego;

import java.util.Collection;
import java.util.Map;

/**
 * Archivos del juego (Valve KeyValues) por héroe, espejados en {@code dotabuff/d2vpkr}. Se define como interfaz para
 * poder mockearla en los tests sin red. NO es OpenDota (son llamadas a GitHub). Solo las llama el calentamiento de
 * {@code DatosMetaService} (al construir o leer el snapshot de meta, en su propio hilo): NINGUNA petición de la API las
 * dispara.
 */
public interface ArchivosJuegoClient {

    /**
     * Pide el archivo de cada héroe y lo interpreta ({@link LectorArchivoHeroe}).
     * <p>
     * Un héroe cuyo archivo no se puede obtener (404, timeout, error) o no trae ninguna habilidad legible se registra
     * por log y simplemente NO aparece en el resultado: nunca falla la carga entera.
     *
     * @param nombresInternos nombre interno de cada héroe ({@code npc_dota_hero_lion}); el archivo se llama igual más
     *                        {@code .txt}
     * @return nombre interno del héroe -> (clave de habilidad -> indicadores)
     */
    Map<String, Map<String, IndicadoresHabilidad>> obtenerIndicadores(Collection<String> nombresInternos);

    /** Sin archivos del juego: siempre vacío (para quien no necesite esta fuente, p. ej. pruebas existentes). */
    ArchivosJuegoClient NINGUNO = nombres -> Map.of();
}
