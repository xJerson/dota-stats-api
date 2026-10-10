package com.dotastats.exception;

public class JugadorNoEncontradoException extends RuntimeException {
    public JugadorNoEncontradoException(long accountId) {
        super("No se encontró el jugador con account_id " + accountId
                + " (perfil privado o inexistente en OpenDota)");
    }
}
