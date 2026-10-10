package com.dotastats.exception;

/**
 * El jugador no expone datos públicos de partidas (o no tiene partidas): OpenDota no puede dar su historial.
 * El mensaje es el que se muestra tal cual al usuario.
 */
public class PerfilPrivadoException extends RuntimeException {
    public PerfilPrivadoException() {
        super("Tu perfil de Dota 2 es privado o no tiene partidas públicas. "
                + "Activa 'Exponer datos públicos de partidas' en Dota 2 (Configuración > Opciones > Social) "
                + "y vuelve a intentar en unos minutos.");
    }
}
