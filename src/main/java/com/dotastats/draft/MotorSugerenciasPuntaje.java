package com.dotastats.draft;

import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.Duelo;
import com.dotastats.meta.HeroeMeta;
import com.dotastats.meta.MatchupsEnemigos;
import com.dotastats.meta.Wilson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Motor de sugerencias basado en datos de meta de OpenDota.
 * <p>
 * Puntaje = (pesoMeta * meta + pesoContra * counter) / (pesoMeta + pesoContra), donde:
 * <ul>
 *   <li>meta: límite inferior de Wilson del winrate del héroe en los brackets cargados;</li>
 *   <li>counter: promedio PONDERADO, sobre todos los enemigos elegidos, de la estimación del winrate del candidato
 *       contra cada uno. La estimación contrae el duelo hacia el meta del candidato:
 *       {@code (victorias + fuerzaPrior * meta) / (partidas + fuerzaPrior)}. Sin datos del duelo (o si la consulta
 *       de ese enemigo falló) vale exactamente el meta, o sea una contribución neutra: ni ventaja ni castigo
 *       frente a quien sí tiene datos, y con muestras pequeñas el duelo apenas se aleja del meta.
 *       El peso de cada enemigo depende del slot en que se puso: {@code pesoOponenteDirecto} si juega contra la
 *       línea de {@code miRol} (ver {@link #oponentesDirectos(int)}) y {@code pesoOtrosEnemigos} si no.</li>
 * </ul>
 * El motivo es honesto con la muestra: nombra a los oponentes directos sin datos suficientes del duelo y, si ningún
 * enemigo tiene datos suficientes, avisa de que el ranking sale solo del meta.
 * Los matchups de cada enemigo se piden de forma perezosa a {@link DatosMetaService}. Las sinergias con
 * aliados no se consideran todavía.
 * <p>
 * Candidatos por posición:
 * <ul>
 *   <li>Posiciones 1-3 con datos de línea (/scenarios/laneRoles, lane_role 1 segura, 2 central, 3 ofensiva): el héroe
 *       entra si su participación en la línea de la posición (partidas en ella / partidas en las líneas 1-3) es
 *       {@code >= umbralLinea} y tiene al menos {@code minPartidasLinea} partidas en esa línea. La posición 1 exige
 *       además una etiqueta de núcleo ({@code Carry}): los soportes también juegan la línea segura.</li>
 *   <li>Si la posición tiene una lista curada ({@code heroesPosicion}, normalmente la 4 y la 5): SOLO esos héroes,
 *       sin exigir etiqueta ni datos de línea. Las etiquetas de OpenDota son demasiado laxas para separar a los
 *       soportes (Enigma o Earth Spirit llevan Support/Initiator) y los datos de línea tampoco lo logran.</li>
 *   <li>Posiciones 4-5 sin lista, o 1-3 sin datos de línea (entonces con una advertencia): al menos una de las
 *       etiquetas de OpenDota configuradas para la posición ({@code roles}).</li>
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

    private static final Logger log = LoggerFactory.getLogger(MotorSugerenciasPuntaje.class);

    private static final int MAX_SUGERENCIAS = 3;
    private static final int MAX_COUNTERS_EN_MOTIVO = 2;
    /** Winrate crudo del duelo desde el cual se considera que el candidato "es fuerte contra" el enemigo. */
    private static final double UMBRAL_COUNTER_FUERTE = 0.52;

    private final DatosMetaService servicio;
    private final PerfilJugadorService perfiles;
    private final SugerenciasProperties props;
    /** Último conjunto de datos contra el que se validaron las listas curadas (se revalida tras cada recarga/parche). */
    private volatile DatosMeta validadoContra;

    public MotorSugerenciasPuntaje(DatosMetaService servicio, PerfilJugadorService perfiles, SugerenciasProperties props) {
        this.servicio = servicio;
        this.perfiles = perfiles;
        this.props = props;
    }

    @Override
    public ResultadoSugerencias sugerir(int miRol, Map<Integer, Integer> enemigos, List<Integer> aliados) {
        return sugerir(miRol, enemigos, aliados, null);
    }

    @Override
    public ResultadoSugerencias sugerir(int miRol, Map<Integer, Integer> enemigosPorSlot, List<Integer> aliados,
                                        Long accountId) {
        DatosMeta datos = servicio.datosParaSugerir();
        validarListas(datos);
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
        // Ordenados por slot para que la consulta de matchups y los motivos sean deterministas
        Map<Integer, Integer> enemigos = new TreeMap<>(enemigosPorSlot);
        List<Integer> idsEnemigos = new ArrayList<>(enemigos.values());
        MatchupsEnemigos matchups = enemigos.isEmpty()
                ? new MatchupsEnemigos(Map.of(), Set.of())
                : servicio.matchupsContra(idsEnemigos);

        Set<Integer> elegidos = new HashSet<>(idsEnemigos);
        elegidos.addAll(aliados);
        List<String> rolesValidos = props.roles().getOrDefault(miRol, List.of());
        Set<String> lista = listaCurada(miRol);
        Integer linea = lineaDe(miRol);
        boolean porLinea = lista.isEmpty() && linea != null && datos.partidasPorLinea().getOrDefault(linea, Map.of()).size() > 0;
        if (lista.isEmpty() && linea != null && !porLinea) {
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
                .filter(h -> !lista.isEmpty()
                        ? lista.contains(HeroeAliasResolver.normalizar(h.nombre()))
                        : porLinea
                        ? juegaLaLinea(h.id(), linea, datos) && cumpleEtiquetaDeNucleo(h, miRol, rolesValidos)
                        : rolesValidos.isEmpty() || h.roles().stream().anyMatch(rolesValidos::contains))
                .map(h -> puntuar(h, miRol, datos, enemigos, matchups, historial))
                .sorted(Comparator.comparingDouble(Sugerencia::puntaje).reversed()
                        .thenComparingInt(Sugerencia::heroId))
                .limit(MAX_SUGERENCIAS)
                .toList();
        advertencias.addAll(advertencias(datos, idsEnemigos, matchups));
        return new ResultadoSugerencias(sugerencias, advertencias);
    }

    /** Nombres normalizados (sin acentos ni mayúsculas) de la lista curada de la posición; vacío si no tiene. */
    private Set<String> listaCurada(int posicion) {
        return props.heroesPosicion().getOrDefault(posicion, List.of()).stream()
                .map(HeroeAliasResolver::normalizar)
                .filter(n -> !n.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Nombres de las listas curadas ("posición N: Nombre") que no corresponden a ningún héroe del catálogo de meta.
     */
    List<String> nombresDesconocidos(DatosMeta datos) {
        Set<String> conocidos = datos.heroes().values().stream()
                .map(h -> HeroeAliasResolver.normalizar(h.nombre()))
                .collect(Collectors.toSet());
        List<String> desconocidos = new ArrayList<>();
        new TreeMap<>(props.heroesPosicion()).forEach((posicion, nombres) -> nombres.stream()
                .filter(n -> !conocidos.contains(HeroeAliasResolver.normalizar(n)))
                .forEach(n -> desconocidos.add("posición " + posicion + ": " + n)));
        return desconocidos;
    }

    /** Avisa (WARN, una vez por conjunto de datos cargado) de los nombres de las listas que no existen en el catálogo. */
    private void validarListas(DatosMeta datos) {
        if (validadoContra == datos) {
            return;
        }
        validadoContra = datos;
        List<String> desconocidos = nombresDesconocidos(datos);
        if (!desconocidos.isEmpty()) {
            log.warn("Las listas de dotastats.sugerencias.heroes-posicion contienen héroes que no existen en el "
                    + "catálogo (revisa la ortografía o si cambiaron de nombre en el parche {}): {}",
                    datos.parche().nombre(), String.join("; ", desconocidos));
        }
    }

    /**
     * Slots del equipo enemigo que juegan contra la línea de {@code posicion} (líneas estándar: la segura
     * -posiciones 1 y 5- enfrenta a la ofensiva rival -3 y 4-, y la central contra la central).
     */
    static Set<Integer> oponentesDirectos(int posicion) {
        return switch (posicion) {
            case 1, 5 -> Set.of(3, 4);
            case 2 -> Set.of(2);
            case 3, 4 -> Set.of(1, 5);
            default -> Set.of();
        };
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

    private Sugerencia puntuar(HeroeMeta heroe, int miRol, DatosMeta datos, Map<Integer, Integer> enemigos,
                               MatchupsEnemigos matchups, RendimientoJugador historial) {
        double meta = Wilson.limiteInferior(heroe.victorias(), heroe.partidas(), props.z());
        Set<Integer> directos = oponentesDirectos(miRol);

        double sumaPonderada = 0;
        double sumaPesos = 0;
        boolean hayDatosSuficientes = false;
        List<Counter> fuertes = new ArrayList<>();
        List<String> sinDatos = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : enemigos.entrySet()) {
            boolean directo = directos.contains(e.getKey());
            double peso = directo ? props.pesoOponenteDirecto() : props.pesoOtrosEnemigos();
            HeroeMeta enemigo = datos.heroes().get(e.getValue());
            Duelo d = matchups.duelo(heroe.id(), e.getValue());
            boolean suficiente = d != null && d.partidas() >= props.minPartidasDuelo();
            double estimacion = d == null || d.partidas() + props.fuerzaPrior() <= 0
                    ? meta
                    : (d.victorias() + props.fuerzaPrior() * meta) / (d.partidas() + props.fuerzaPrior());
            sumaPonderada += peso * estimacion;
            sumaPesos += peso;
            if (suficiente) {
                hayDatosSuficientes = true;
                if (enemigo != null && d.winrate() >= UMBRAL_COUNTER_FUERTE) {
                    fuertes.add(new Counter(enemigo.nombre(), d.winrate()));
                }
            } else if (directo && enemigo != null) {
                sinDatos.add(enemigo.nombre());
            }
        }

        double puntaje = meta;
        if (sumaPesos > 0) {
            double counter = sumaPonderada / sumaPesos;
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
        boolean soloMeta = !enemigos.isEmpty() && !hayDatosSuficientes;
        return new Sugerencia(heroe.id(), heroe.nombre(), motivo(heroe, fuertes, sinDatos, soloMeta, propio), puntaje);
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

    private String motivo(HeroeMeta heroe, List<Counter> fuertes, List<String> sinDatos, boolean soloMeta,
                          Duelo propio) {
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
        if (soloMeta) {
            sb.append("; ranking según el meta, sin datos de duelo suficientes");
        } else if (!sinDatos.isEmpty()) {
            sb.append("; sin datos suficientes contra ")
                    .append(String.join(" y ", sinDatos.stream().limit(MAX_COUNTERS_EN_MOTIVO).toList()));
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
