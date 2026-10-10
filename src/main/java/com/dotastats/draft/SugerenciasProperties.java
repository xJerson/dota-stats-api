package com.dotastats.draft;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración del motor de sugerencias (prefijo {@code dotastats.sugerencias}).
 *
 * @param pesoMeta          peso del winrate de meta (Wilson) en el puntaje
 * @param pesoContra        peso del componente counter (promedio Wilson contra los enemigos elegidos)
 * @param minPartidasDuelo  partidas mínimas de un duelo para que el motivo lo cite como "fuerte contra" un enemigo
 * @param fuerzaPrior       partidas "virtuales" con el winrate de meta del candidato que se suman a cada duelo
 *                          (contracción hacia el meta): con pocos datos el duelo vale casi lo mismo que el meta
 * @param z                cuantil de Wilson (1.96 = 95%)
 * @param pesoPersonal      peso (0..1) del componente personal cuando la petición trae steamId; meta y counter se
 *                          reescalan proporcionalmente por (1 - pesoPersonal) para que los pesos sigan sumando 1
 * @param fuerzaPriorPersonal partidas "virtuales" con el winrate de meta del candidato que se suman al historial
 *                          del jugador con ese héroe: sin partidas el componente personal vale exactamente el meta
 * @param minPartidasPersonal partidas mínimas del jugador con el héroe para citar su winrate en el motivo
 * @param umbralLinea       posiciones 1-3: participación mínima (0..1) del héroe en la línea de esa posición, sobre
 *                          sus partidas totales en las líneas 1-3 (segura, central, ofensiva). La posición 1 exige
 *                          además alguna etiqueta de {@code roles[1]}, porque los soportes también juegan la segura
 * @param minPartidasLinea  posiciones 1-3: partidas mínimas del héroe en esa línea para ser candidato
 * @param roles             posición (1-5) -> roles de OpenDota aceptados; ver {@link #ROLES_POR_DEFECTO}. Se usa
 *                          siempre en las posiciones 4-5 y, en las 1-3, solo si no hay datos de línea
 * @param excluirPosicion   posición (1-5) -> nombres de héroe que nunca se sugieren en ella (anulación manual)
 */
@ConfigurationProperties("dotastats.sugerencias")
public record SugerenciasProperties(@DefaultValue("0.4") double pesoMeta,
                                    @DefaultValue("0.6") double pesoContra,
                                    @DefaultValue("10") int minPartidasDuelo,
                                    @DefaultValue("100") double fuerzaPrior,
                                    @DefaultValue("1.96") double z,
                                    @DefaultValue("0.25") double pesoPersonal,
                                    @DefaultValue("20") double fuerzaPriorPersonal,
                                    @DefaultValue("5") int minPartidasPersonal,
                                    @DefaultValue("0.30") double umbralLinea,
                                    @DefaultValue("30") long minPartidasLinea,
                                    Map<Integer, List<String>> roles,
                                    Map<Integer, List<String>> excluirPosicion) {

    /**
     * Heurística, no una verdad del juego: OpenDota no sabe en qué posición se juega un héroe, solo le
     * pone etiquetas de rol. Un héroe es candidato a una posición si tiene AL MENOS UNA de sus etiquetas.
     * Para las posiciones 1-3 es solo el plan B cuando faltan los datos de línea (ver {@link #umbralLinea}).
     * <ul>
     *   <li>1 (carry): Carry</li>
     *   <li>2 (mid): Nuker, Carry</li>
     *   <li>3 (offlane): Initiator, Durable</li>
     *   <li>4 (soft support): Support, Initiator</li>
     *   <li>5 (hard support): Support</li>
     * </ul>
     */
    public static final Map<Integer, List<String>> ROLES_POR_DEFECTO = Map.of(
            1, List.of("Carry"),
            2, List.of("Nuker", "Carry"),
            3, List.of("Initiator", "Durable"),
            4, List.of("Support", "Initiator"),
            5, List.of("Support"));

    public SugerenciasProperties {
        // Lo configurado por posición pisa el valor por defecto; las posiciones sin configurar lo conservan.
        Map<Integer, List<String>> completo = new HashMap<>(ROLES_POR_DEFECTO);
        if (roles != null) {
            completo.putAll(roles);
        }
        roles = Map.copyOf(completo);
        excluirPosicion = excluirPosicion == null ? Map.of() : Map.copyOf(excluirPosicion);
    }

    public static SugerenciasProperties porDefecto() {
        return new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 5, 0.30, 30, null, null);
    }
}
