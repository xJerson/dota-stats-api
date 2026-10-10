package com.dotastats.draft;

import java.util.List;

/**
 * Punto de extensión del asesor de draft. Recibe solo héroes ya resueltos (ids del catálogo)
 * para que el motor no dependa de cómo el usuario escribió los nombres.
 */
public interface MotorSugerencias {

    ResultadoSugerencias sugerir(int miRol, List<Integer> enemigos, List<Integer> aliados);

    /**
     * Igual que {@link #sugerir(int, List, List)} pero personalizando con el historial del jugador.
     *
     * @param accountId account_id (SteamID32) del jugador; null equivale a no personalizar
     * @throws com.dotastats.exception.PerfilPrivadoException si el jugador no tiene datos públicos
     */
    default ResultadoSugerencias sugerir(int miRol, List<Integer> enemigos, List<Integer> aliados, Long accountId) {
        return sugerir(miRol, enemigos, aliados);
    }
}
