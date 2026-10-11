package com.dotastats.analizadores.habilidad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de la fila "Sugerencias por jugabilidad" (prefijo {@code dotastats.habilidad}).
 *
 * @param pesoConfianzaAlta  puntos que aporta una regla ESCRITA A MANO de confianza "alta" por cada rival directo
 * @param pesoConfianzaMedia puntos que aporta una regla escrita a mano de confianza "media"; también la base
 *                           sobre la que se calcula el peso de las interacciones AUTOMÁTICAS (ver
 *                           {@link Automatico#factorPeso()}), porque esas SIEMPRE tienen confianza "media"
 * @param pesoConfianzaBaja  puntos que aporta una regla escrita a mano de confianza "baja"
 * @param reglasArchivo      ubicación del archivo de reglas (prefijo {@code classpath:} o {@code file:}; ver
 *                           {@link org.springframework.core.io.ResourceLoader}); por defecto el que trae el jar
 * @param automatico         configuración de las interacciones automáticas derivadas de
 *                           {@code /constants/abilities} (ver {@link CatalogoHabilidades})
 */
@ConfigurationProperties("dotastats.habilidad")
public record HabilidadProperties(
        @DefaultValue("1.0") double pesoConfianzaAlta,
        @DefaultValue("0.5") double pesoConfianzaMedia,
        @DefaultValue("0.25") double pesoConfianzaBaja,
        @DefaultValue("classpath:reglas-habilidades.json") String reglasArchivo,
        Automatico automatico,
        Dependencias dependencias) {

    /**
     * @param factorPeso multiplicador (0..1) sobre {@code pesoConfianzaMedia} que da el peso BASE de una
     *                   interacción automática (antes del multiplicador propio de cada combinación rasgo/
     *                   capacidad, ver {@code TablaInteraccionesAutomaticas}): siempre por debajo de una regla
     *                   escrita a mano de la misma confianza, porque es una heurística por palabra clave y no
     *                   una regla verificada a mano
     */
    public record Automatico(@DefaultValue("0.5") double factorPeso) {
    }

    /**
     * @param archivo ubicación del archivo curado de dependencias de héroes enemigos y herramientas de los
     *                candidatos (prefijo {@code classpath:} o {@code file:}); por defecto el que trae el jar
     * @param rangoMinimo diferencia mínima de alcance de ataque base (a favor del candidato) para que cuente como
     *                    herramienta RANGO_MAYOR contra una dependencia de acoso a distancia; la diferencia debe ser
     *                    mayor o igual a este valor
     */
    public record Dependencias(@DefaultValue("classpath:dependencias-heroes.json") String archivo,
                               @DefaultValue("50") int rangoMinimo) {
    }

    // OJO: a propósito un único constructor (el canónico). Spring Boot enlaza un record de
    // @ConfigurationProperties por reflexión sobre "el" constructor; si hay más de uno (aunque solo uno sea
    // público) deja de reconocer cuál usar y el arranque falla con "No default constructor found". Por eso
    // NO hay un constructor de compatibilidad de 4 campos: quien no necesite personalizar lo automático ni las
    // dependencias pasa null (el compacto lo normaliza a los valores por defecto).
    public HabilidadProperties {
        if (automatico == null) {
            automatico = new Automatico(0.5);
        }
        if (dependencias == null) {
            dependencias = new Dependencias("classpath:dependencias-heroes.json", 50);
        }
    }

    /** Peso de una confianza ("alta"/"media"/"baja"); 0 para cualquier otro valor (no debería llegar, se valida al cargar). */
    public double peso(String confianza) {
        return switch (confianza) {
            case "alta" -> pesoConfianzaAlta;
            case "media" -> pesoConfianzaMedia;
            case "baja" -> pesoConfianzaBaja;
            default -> 0.0;
        };
    }

    /** Peso base (antes del multiplicador de la tabla) de cualquier interacción automática. */
    public double pesoBaseAutomatico() {
        return pesoConfianzaMedia * automatico.factorPeso();
    }
}
