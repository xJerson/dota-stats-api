package com.dotastats.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SteamIdConverterTest {

    @Test
    void convierteSteamId64AAccountId() {
        // 76561197960265728 + 123456789 = 76561198083722517
        long resultado = SteamIdConverter.toAccountId("76561198083722517");
        assertEquals(123456789L, resultado);
    }

    @Test
    void dejaAccountId32SinCambios() {
        long resultado = SteamIdConverter.toAccountId("123456789");
        assertEquals(123456789L, resultado);
    }

    @Test
    void rechazaSteamIdNoNumerico() {
        assertThrows(IllegalArgumentException.class,
                () -> SteamIdConverter.toAccountId("abc123"));
    }

    @Test
    void rechazaSteamIdNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> SteamIdConverter.toAccountId(null));
    }
}
