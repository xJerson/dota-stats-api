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

        RendimientoJugador historial = personal;
        List<Sugerencia> sugerencias = datos.heroes().values().stream()
                .filter(h -> !elegidos.contains(h.id()))
                .filter(h -> rolesValidos.isEmpty() || h.roles().stream().anyMatch(rolesValidos::contains))
                .map(h -> puntuar(h, datos, enemigos, matchups, historial))
                .sorted(Comparator.comparingDouble(Sugerencia::puntaje).reversed()
                        .thenComparingInt(Sugerencia::heroId))
                .limit(MAX_SUGERENCIAS)
                .toList();
        advertencias.addAll(advertencias(datos, enemigos, matchups));
        return new ResultadoSugerencias(sugerencias, advertencias);
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
