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
 * @param roles             posición (1-5) -> roles de OpenDota aceptados; ver {@link #ROLES_POR_DEFECTO}
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
                                    Map<Integer, List<String>> roles) {

    /**
     * Heurística, no una verdad del juego: OpenDota no sabe en qué posición se juega un héroe, solo le
     * pone etiquetas de rol. Un héroe es candidato a una posición si tiene AL MENOS UNA de sus etiquetas.
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
    }

    public static SugerenciasProperties porDefecto() {
        return new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 5, null);
    }
}
