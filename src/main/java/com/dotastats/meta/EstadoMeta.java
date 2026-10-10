package com.dotastats.meta;

import java.time.Instant;
import java.util.List;

/**
 * Estado de los datos de meta para GET /api/admin/estado.
 *
 * @param cargado             hay datos de meta (heroStats + parche) en memoria
 * @param cargando            hay una carga de los datos base en curso
 * @param parche              parche de los datos cargados (null si no hay)
 * @param parcheActual        último parche publicado por OpenDota (null si no se pudo consultar)
 * @param obsoleto            true si hay datos cargados y el parche vivo es distinto al cargado; el siguiente
 *                            sugerir descarta la caché y recarga
 * @param matchupsEnCache     cuántos héroes tienen sus matchups en la caché
 * @param heroesConMatchups   ids de esos héroes, ordenados
 * @param cacheDiscoUsada     true si los datos actuales salieron del archivo de caché y no de OpenDota
 * @param ultimoError         mensaje del último fallo de carga (null si la última carga fue bien)
 */
public record EstadoMeta(boolean cargado,
                         boolean cargando,
                         String parche,
                         String parcheActual,
                         Instant cargadoEn,
                         boolean obsoleto,
                         int matchupsEnCache,
                         List<Integer> heroesConMatchups,
                         boolean cacheDiscoUsada,
                         String ultimoError) {
}
