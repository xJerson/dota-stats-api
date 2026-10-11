package com.dotastats.analizadores.habilidad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de lo que se DERIVA de los archivos del juego (prefijo {@code dotastats.habilidad.juego}, ver
 * AGENTS.md 5.3).
 *
 * @param derivarPasivas si true, las pasivas rompibles de un enemigo sin dependencias curadas también se derivan como
 *                       dependencia {@code PASIVAS}. Por defecto false: casi todos los héroes tienen alguna pasiva
 *                       rompible, así que las mismas 4 herramientas de break/desarme saldrían repetidas contra
 *                       todos y taparían a los counters útiles. Por defecto solo se deriva el escape.
 */
@ConfigurationProperties("dotastats.habilidad.juego")
public record JuegoProperties(@DefaultValue("false") boolean derivarPasivas) {
}
