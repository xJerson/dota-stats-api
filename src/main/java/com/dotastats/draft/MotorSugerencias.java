package com.dotastats.draft;

import java.util.List;
import java.util.Map;

/**
 * Punto de extensión del asesor de draft. Recibe solo héroes ya resueltos (ids del catálogo)
 * para que el motor no dependa de cómo el usuario escribió los nombres.
 * <p>
 * Los enemigos llegan por slot (posición 1-5 del rival -> heroId): la posición en que el usuario puso a cada
 * enemigo importa, porque no es lo mismo el mid rival que el offlane rival para la línea de quien pregunta.
 */
public interface MotorSugerencias {

    ResultadoSugerencias sugerir(int miRol, Map<Integer, Integer> enemigos, List<Integer> aliados);

    /**
     * Igual que {@link #sugerir(int, Map, List)} pero personalizando con el historial del jugador.
     *
     * @param accountId account_id (SteamID32) del jugador; null equivale a no personalizar
     * @throws com.dotastats.exception.PerfilPrivadoException si el jugador no tiene datos públicos
     */
    default ResultadoSugerencias sugerir(int miRol, Map<Integer, Integer> enemigos, List<Integer> aliados,
                                         Long accountId) {
        return sugerir(miRol, enemigos, aliados);
    }
}
