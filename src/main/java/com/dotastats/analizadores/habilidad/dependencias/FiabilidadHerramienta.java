package com.dotastats.analizadores.habilidad.dependencias;

/**
 * FÓRMULA DE FIABILIDAD de una herramienta (un efecto curado de un candidato), en un solo lugar. Devuelve un
 * factor en {@code [0.05, 1.0]} que multiplica el peso de la herramienta; todos los números salen de los
 * campos estructurados de OpenDota a NIVEL 1 (nunca del texto de la descripción):
 * <pre>
 *   fiabilidad = 0.40                                   (base)
 *              + 0.30 * min(duración, 3 s) / 3 s        (duración del efecto; sin duración conocida: +0.15)
 *              + bonoDeLanzamiento(puntoDeLanzamiento + retraso)
 *                    t &lt;= 0    -&gt; +0.15   (instantáneo)
 *                    t &lt;= 0.3  -&gt; +0.10
 *                    t &lt;= 0.5  -&gt; +0.05
 *                    t &lt;= 1.0  -&gt;  0.00
 *                    t &gt;  1.0  -&gt; -0.10
 *              + 0.15 si bkbpierce == "Yes"             (atraviesa la inmunidad a la magia)
 *   si es ultimate: fiabilidad *= 0.5                   (solo existe desde el nivel 6, penalizada en línea)
 * </pre>
 * Es monótona: más duración, menos tiempo hasta el efecto o atravesar la inmunidad nunca bajan el resultado,
 * y una ultimate nunca vale más que la misma habilidad sin serlo. El máximo sin penalizar es 1.0.
 */
public final class FiabilidadHerramienta {

    /** Duración (s) a partir de la cual más duración ya no suma. */
    public static final double DURACION_TOPE = 3.0;
    /** Multiplicador de una ultimate en la fase de línea. */
    public static final double FACTOR_ULTIMATE = 0.5;

    private static final double BASE = 0.40;
    private static final double MAX_POR_DURACION = 0.30;
    private static final double DURACION_DESCONOCIDA = 0.15;
    private static final double BONO_BKB = 0.15;
    private static final double MINIMO = 0.05;
    private static final double MAXIMO = 1.0;

    private FiabilidadHerramienta() {
    }

    /**
     * @param duracion          duración del efecto a nivel 1 en segundos; null si la herramienta no tiene una
     * @param puntoLanzamiento  {@code abilitycastpoint} a nivel 1 (0 = instantáneo)
     * @param retraso           segundos entre el lanzamiento y el efecto (p. ej. el retraso de un área); 0 si no hay
     * @param atraviesaBkb      {@code bkbpierce == "Yes"}
     * @param esUlti            true si la habilidad es la ultimate del candidato
     */
    public static double calcular(Double duracion, double puntoLanzamiento, double retraso,
                                  boolean atraviesaBkb, boolean esUlti) {
        double total = BASE;
        total += duracion == null
                ? DURACION_DESCONOCIDA
                : MAX_POR_DURACION * Math.min(Math.max(duracion, 0.0), DURACION_TOPE) / DURACION_TOPE;
        total += bonoDeLanzamiento(Math.max(puntoLanzamiento, 0.0) + Math.max(retraso, 0.0));
        if (atraviesaBkb) {
            total += BONO_BKB;
        }
        if (esUlti) {
            total *= FACTOR_ULTIMATE;
        }
        return Math.max(MINIMO, Math.min(MAXIMO, total));
    }

    /**
     * Fiabilidad de la herramienta derivada del alcance de ataque base (sin curar): crece con la diferencia de
     * alcance, entre 0.4 y 1.0 (a partir de 150 unidades de ventaja ya es máxima).
     */
    public static double deAlcance(int diferencia) {
        return Math.max(BASE, Math.min(MAXIMO, BASE + Math.max(diferencia, 0) / 250.0));
    }

    private static double bonoDeLanzamiento(double tiempoHastaElEfecto) {
        if (tiempoHastaElEfecto <= 0.0) {
            return 0.15;
        }
        if (tiempoHastaElEfecto <= 0.3) {
            return 0.10;
        }
        if (tiempoHastaElEfecto <= 0.5) {
            return 0.05;
        }
        if (tiempoHastaElEfecto <= 1.0) {
            return 0.0;
        }
        return -0.10;
    }
}
