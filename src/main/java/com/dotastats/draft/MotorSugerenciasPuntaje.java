package com.dotastats.draft;

import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.Duelo;
import com.dotastats.meta.HeroeMeta;
import com.dotastats.meta.MatchupsEnemigos;
import com.dotastats.meta.Wilson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Motor de sugerencias basado en datos de meta de OpenDota.
 * <p>
 * Puntaje = (pesoMeta * meta + pesoContra * counter) / (pesoMeta + pesoContra), donde:
 * <ul>
 *   <li>meta: límite inferior de Wilson del winrate del héroe en los brackets cargados;</li>
 *   <li>counter: promedio, sobre todos los enemigos elegidos, de la estimación del winrate del candidato contra
 *       cada uno. La estimación contrae el duelo hacia el meta del candidato:
 *       {@code (victorias + fuerzaPrior * meta) / (partidas + fuerzaPrior)}. Sin datos del duelo (o si la consulta
 *       de ese enemigo falló) vale exactamente el meta, o sea una contribución neutra: ni ventaja ni castigo
 *       frente a quien sí tiene datos, y con muestras pequeñas el duelo apenas se aleja del meta.</li>
 * </ul>
 * Los matchups de cada enemigo se piden de forma perezosa a {@link DatosMetaService}. Las sinergias con
 * aliados no se consideran todavía.
 * <p>
 * Candidatos por posición:
 * <ul>
 *   <li>Posiciones 1-3 con datos de línea (/scenarios/laneRoles, lane_role 1 segura, 2 central, 3 ofensiva): el héroe
 *       entra si su participación en la línea de la posición (partidas en ella / partidas en las líneas 1-3) es
 *       {@code >= umbralLinea} y tiene al menos {@code minPartidasLinea} partidas en esa línea. La posición 1 exige
 *       además una etiqueta de núcleo ({@code Carry}): los soportes también juegan la línea segura.</li>
 *   <li>Posiciones 4-5, o 1-3 sin datos de línea (entonces con una advertencia): al menos una de las etiquetas de
 *       OpenDota configuradas para la posición (los datos de línea no distinguen a los soportes: juegan en la
 *       línea segura u ofensiva igual que un núcleo).</li>
 *   <li>Aparte, la lista {@code excluirPosicion} quita héroes concretos de una posición.</li>
 * </ul>
 * <p>
 * Personalización opcional (petición con cuenta de jugador): se añade un componente personal con peso
 * {@code w = pesoPersonal} y el resto se reescala por {@code (1 - w)}, de modo que los pesos siguen sumando 1:
 * {@code puntaje = (1 - w) * base + w * personal}. El componente personal contrae el historial del jugador con el
 * héroe hacia el meta del candidato: {@code (victorias + K * meta) / (partidas + K)} con
 * {@code K = fuerzaPriorPersonal}. Un héroe nunca jugado vale exactamente el meta (neutro, sin castigo).
 */
@Component
public class MotorSugerenciasPuntaje implements MotorSugerencias {

    private static final int MAX_SUGERENCIAS = 3;
    private static final int MAX_COUNTERS_EN_MOTIVO = 2;
    /** Winrate crudo del duelo desde el cual se considera que el candidato "es fuerte contra" el enemigo. */
    private static final double UMBRAL_COUNTER_FUERTE = 0.52;

    private final DatosMetaService servicio;
    private final PerfilJugadorService perfiles;
    private final SugerenciasProperties props;

    public MotorSugerenciasPuntaje(DatosMetaService servicio, PerfilJugadorService perfiles, SugerenciasProperties props) {
        this.servicio = servicio;
        this.perfiles = perfiles;
        this.props = props;
    }

    @Override
    public ResultadoSugerencias sugerir(int miRol, List<Integer> enemigos, List<Integer> aliados) {
        return sugerir(miRol, enemigos, aliados, null);
    }

    @Override
    public ResultadoSugerencias sugerir(int miRol, List<Integer> enemigos, List<Integer> aliados, Long accountId) {
        DatosMeta datos = servicio.datosParaSugerir();
        List<String> advertencias = new ArrayList<>();
        RendimientoJugador personal = null;
        if (accountId != null) {
            // Primero el perfil (una sola petición, cacheada): si es privado se falla rápido sin descargar matchups.
            try {
                personal = perfiles.obtener(accountId);
            } catch (OpenDotaNoDisponibleException e) {
                advertencias.add("No se pudieron obtener tus estadísticas personales desde OpenDota; "
                        + "las sugerencias no están personalizadas.");
            }
        }
        MatchupsEnemigos matchups = enemigos.isEmpty()
                ? new MatchupsEnemigos(Map.of(), Set.of())
                : servicio.matchupsContra(enemigos);

        Set<Integer> elegidos = new HashSet<>(enemigos);
        elegidos.addAll(aliados);
        List<String> rolesValidos = props.roles().getOrDefault(miRol, List.of());
        Integer linea = lineaDe(miRol);
        boolean porLinea = linea != null && datos.partidasPorLinea().getOrDefault(linea, Map.of()).size() > 0;
        if (linea != null && !porLinea) {
            advertencias.add("No hay datos de línea de OpenDota; las sugerencias de la posición " + miRol
                    + " se filtraron solo por las etiquetas de rol de los héroes, así que pueden incluir héroes "
                    + "que no suelen jugarse ahí.");
        }
        Set<String> excluidos = props.excluirPosicion().getOrDefault(miRol, List.of()).stream()
                .map(n -> n.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        RendimientoJugador historial = personal;
        List<Sugerencia> sugerencias = datos.heroes().values().stream()
                .filter(h -> !elegidos.contains(h.id()))
                .filter(h -> !excluidos.contains(h.nombre().toLowerCase(Locale.ROOT)))
                .filter(h -> porLinea
                        ? juegaLaLinea(h.id(), linea, datos) && cumpleEtiquetaDeNucleo(h, miRol, rolesValidos)
                        : rolesValidos.isEmpty() || h.roles().stream().anyMatch(rolesValidos::contains))
                .map(h -> puntuar(h, datos, enemigos, matchups, historial))
                .sorted(Comparator.comparingDouble(Sugerencia::puntaje).reversed()
                        .thenComparingInt(Sugerencia::heroId))
                .limit(MAX_SUGERENCIAS)
                .toList();
        advertencias.addAll(advertencias(datos, enemigos, matchups));
        return new ResultadoSugerencias(sugerencias, advertencias);
    }

    /** lane_role de OpenDota que corresponde a una posición núcleo (1-3); null para las posiciones 4-5. */
    private static Integer lineaDe(int posicion) {
        return posicion >= 1 && posicion <= 3 ? posicion : null;
    }

    /**
     * La línea segura la juegan también los soportes (la línea es de la lane, no de la posición), así que la posición 1
     * exige además una etiqueta de núcleo (por defecto {@code Carry}). En la central y la ofensiva solo cuenta la línea.
     */
    private static boolean cumpleEtiquetaDeNucleo(HeroeMeta h, int posicion, List<String> rolesValidos) {
        return posicion != 1 || rolesValidos.isEmpty() || h.roles().stream().anyMatch(rolesValidos::contains);
    }

    private boolean juegaLaLinea(int heroId, int linea, DatosMeta datos) {
        long total = 0;
        for (int l = 1; l <= 3; l++) {
            total += datos.partidasPorLinea().getOrDefault(l, Map.of()).getOrDefault(heroId, 0L);
        }
        long enLinea = datos.partidasPorLinea().getOrDefault(linea, Map.of()).getOrDefault(heroId, 0L);
        return total > 0 && enLinea >= props.minPartidasLinea() && (double) enLinea / total >= props.umbralLinea();
    }

    private Sugerencia puntuar(HeroeMeta heroe, DatosMeta datos, List<Integer> enemigos, MatchupsEnemigos matchups,
                               RendimientoJugador historial) {
        double meta = Wilson.limiteInferior(heroe.victorias(), heroe.partidas(), props.z());

        double sumaCounter = 0;
        List<Counter> fuertes = new ArrayList<>();
        for (int enemigoId : enemigos) {
            Duelo d = matchups.duelo(heroe.id(), enemigoId);
            if (d == null || d.partidas() + props.fuerzaPrior() <= 0) {
                sumaCounter += meta;
                continue;
            }
            sumaCounter += (d.victorias() + props.fuerzaPrior() * meta) / (d.partidas() + props.fuerzaPrior());
            HeroeMeta enemigo = datos.heroes().get(enemigoId);
            if (enemigo != null && d.partidas() >= props.minPartidasDuelo() && d.winrate() >= UMBRAL_COUNTER_FUERTE) {
                fuertes.add(new Counter(enemigo.nombre(), d.winrate()));
            }
        }

        double puntaje = meta;
        if (!enemigos.isEmpty()) {
            double counter = sumaCounter / enemigos.size();
            puntaje = (props.pesoMeta() * meta + props.pesoContra() * counter)
                    / (props.pesoMeta() + props.pesoContra());
        }

        Duelo propio = null;
        if (historial != null) {
            propio = historial.de(heroe.id());
            double w = Math.max(0.0, Math.min(1.0, props.pesoPersonal()));
            double k = props.fuerzaPriorPersonal();
            double personal = propio == null || propio.partidas() + k <= 0
                    ? meta
                    : (propio.victorias() + k * meta) / (propio.partidas() + k);
            puntaje = (1 - w) * puntaje + w * personal;
        }
        return new Sugerencia(heroe.id(), heroe.nombre(), motivo(heroe, fuertes, propio), puntaje);
    }

    private static List<String> advertencias(DatosMeta datos, List<Integer> enemigos, MatchupsEnemigos matchups) {
        List<String> nombres = enemigos.stream()
                .filter(matchups.fallidos()::contains)
                .map(id -> datos.heroes().containsKey(id) ? datos.heroes().get(id).nombre() : "héroe " + id)
                .toList();
        if (nombres.isEmpty()) {
            return List.of();
        }
        return List.of("No se pudieron obtener los enfrentamientos contra " + String.join(", ", nombres)
                + " desde OpenDota; para " + (nombres.size() > 1 ? "esos enemigos" : "ese enemigo")
                + " se usó solo el meta.");
    }

    private String motivo(HeroeMeta heroe, List<Counter> fuertes, Duelo propio) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%.1f%% en el meta", heroe.winrate() * 100));
        List<Counter> mejores = fuertes.stream()
                .sorted(Comparator.comparingDouble(Counter::winrate).reversed())
                .limit(MAX_COUNTERS_EN_MOTIVO)
                .toList();
        if (!mejores.isEmpty()) {
            sb.append("; fuerte contra ");
            for (int i = 0; i < mejores.size(); i++) {
                if (i > 0) {
                    sb.append(" y ");
                }
                Counter c = mejores.get(i);
                sb.append(String.format(Locale.ROOT, "%s (%.0f%%)", c.nombre(), c.winrate() * 100));
            }
        }
        if (propio != null && propio.partidas() >= props.minPartidasPersonal()) {
            sb.append(String.format(Locale.ROOT, "; tu winrate: %.0f%% en %d %s", propio.winrate() * 100,
                    propio.partidas(), propio.partidas() == 1 ? "partida" : "partidas"));
        }
        return sb.toString();
    }

    private record Counter(String nombre, double winrate) {
    }
}
