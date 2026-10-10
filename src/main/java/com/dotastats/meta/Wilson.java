package com.dotastats.meta;

/**
 * Límite inferior del intervalo de confianza de Wilson para una proporción. Sirve para que un
 * 60% sobre 20 partidas valga menos que un 53% sobre 20.000: con poca muestra el límite cae mucho.
 */
public final class Wilson {

    private Wilson() {
    }

    /**
     * @param victorias victorias observadas
     * @param partidas  partidas observadas
     * @param z         cuantil normal (1.96 = 95% de confianza)
     * @return límite inferior en [0, 1]; 0 si no hay partidas
     */
    public static double limiteInferior(long victorias, long partidas, double z) {
        if (partidas <= 0) {
            return 0.0;
        }
        double n = partidas;
        double p = Math.min(1.0, (double) victorias / n);
        double z2 = z * z;
        double centro = p + z2 / (2 * n);
        double margen = z * Math.sqrt(p * (1 - p) / n + z2 / (4 * n * n));
        return Math.max(0.0, (centro - margen) / (1 + z2 / n));
    }
}
