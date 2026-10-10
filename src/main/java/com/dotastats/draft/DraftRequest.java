package com.dotastats.draft;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Map;

/**
 * Estado del draft. Las claves de enemigos/aliados son el slot (1..5); un valor nulo
 * significa que ese slot todavía no tiene héroe elegido. Si no se envía un equipo se
 * interpreta como vacío.
 */
public record DraftRequest(
        @Min(value = 1, message = "miRol debe estar entre 1 y 5")
        @Max(value = 5, message = "miRol debe estar entre 1 y 5")
        int miRol,
        Map<@Min(value = 1, message = "el slot debe estar entre 1 y 5")
        @Max(value = 5, message = "el slot debe estar entre 1 y 5") Integer, String> enemigos,
        Map<@Min(value = 1, message = "el slot debe estar entre 1 y 5")
        @Max(value = 5, message = "el slot debe estar entre 1 y 5") Integer, String> aliados,
        String steamId) {

    /** Opcional: SteamID64 o account_id. Si viene, las sugerencias se personalizan con el historial del jugador. */
    public boolean personaliza() {
        return steamId != null && !steamId.isBlank();
    }

    public DraftRequest {
        enemigos = enemigos == null ? Map.of() : enemigos;
        aliados = aliados == null ? Map.of() : aliados;
    }
}
