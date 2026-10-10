package com.dotastats.meta;

/**
 * Resultado de un héroe contra otro: partidas jugadas y victorias del primero.
 */
public record Duelo(int partidas, int victorias) {

    public double winrate() {
        return partidas == 0 ? 0.0 : (double) victorias / partidas;
    }

    /** El mismo duelo visto desde el rival: sus victorias son las partidas que no ganó el primero. */
    public Duelo complemento() {
        return new Duelo(partidas, partidas - victorias);
    }
}
