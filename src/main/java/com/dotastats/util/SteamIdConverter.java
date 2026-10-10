package com.dotastats.util;

/**
 * OpenDota identifica jugadores por su account_id (SteamID32), pero los usuarios
 * normalmente solo conocen su SteamID64 (el que aparece en la URL de su perfil de Steam).
 * Esta clase detecta cuál te pasaron y siempre devuelve el account_id que espera OpenDota.
 */
public final class SteamIdConverter {

    // Offset fijo entre SteamID64 y SteamID32 (account_id), definido por Valve.
    private static final long STEAM_ID_64_BASE = 76561197960265728L;

    private SteamIdConverter() {
    }

    public static long toAccountId(String steamId) {
        if (steamId == null || !steamId.matches("\\d+")) {
            throw new IllegalArgumentException("Steam ID inválido: debe ser numérico");
        }

        long valor = Long.parseLong(steamId);

        if (valor >= STEAM_ID_64_BASE) {
            return valor - STEAM_ID_64_BASE;
        }
        return valor;
    }
}
