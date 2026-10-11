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
 * Puntaje = (pesoMeta * metaPosicion + pesoContra * counter) / (pesoMeta + pesoContra), donde:
 * <ul>
 *   <li>metaPosicion: meta del héroe AJUSTADO por posición. Se parte del meta global (límite inferior de Wilson del
 *       winrate en los brackets cargados) y, si {@code miRol} mapea a una línea de {@code /scenarios/laneRoles}
 *       (posiciones 1-3; ver {@link #lineaDe(int)}) y hay datos de esa línea para el héroe, se contrae hacia el
 *       winrate real del héroe EN esa línea: {@code (victoriasLinea + fuerzaPriorLinea*metaGlobal) / (partidasLinea
 *       + fuerzaPriorLinea)}. Sin datos de línea (o en las posiciones 4-5, sin línea mapeada) vale exactamente el
 *       meta global.</li>
 *   <li>counter: promedio PONDERADO, sobre todos los enemigos elegidos, de la estimación del winrate del candidato
 *       contra cada uno. La estimación contrae el duelo hacia el metaPosicion del candidato:
 *       {@code (victorias + fuerzaPrior * metaPosicion) / (partidas + fuerzaPrior)}. Sin datos del duelo (o si la
 *       consulta de ese enemigo falló) vale exactamente el metaPosicion, o sea una contribución neutra: ni ventaja
 *       ni castigo frente a quien sí tiene datos, y con muestras pequeñas el duelo apenas se aleja del metaPosicion.
 *       El peso de cada enemigo depende del slot en que se puso: {@code pesoOponenteDirecto} si juega contra la
 *       línea de {@code miRol} (ver {@link #oponentesDirectos(int)}) y {@code pesoOtrosEnemigos} si no.</li>
 * </ul>
 * El motivo es honesto con la muestra: solo cita un duelo como tendencia (positiva o negativa) cuando tiene al
 * menos {@code partidasMinimasDuelo} partidas Y su estimación se aleja del metaPosicion al menos
 * {@code ventajaMinimaDuelo}; nunca dice "fuerte contra". Nombra además a los oponentes directos sin datos
 * suficientes y, si ningún enemigo aporta una tendencia citable, avisa de que el ranking sale solo del meta, sin
 * ventaja de duelo relevante.
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
 * héroe hacia el metaPosicion del candidato: {@code (victorias + K * metaPosicion) / (partidas + K)} con
 * {@code K = fuerzaPriorPersonal}. Un héroe nunca jugado vale exactamente el metaPosicion (neutro, sin castigo).
 */
@Component
public class MotorSugerenciasPuntaje implements MotorSugerencias {

    private static final Logger log = LoggerFactory.getLogger(MotorSugerenciasPuntaje.class);

    /** Sugerencias por fila; reutilizada por {@code ServicioSugerencias} para recortar cada fila tras el deduplicado. */
    public static final int MAX_SUGERENCIAS = 3;
    private static final int MAX_TENDENCIAS_EN_MOTIVO = 2;
    /** Nombre de la línea (lane_role de OpenDota) tal como se cita en el motivo, por posición núcleo (1-3). */
    private static final Map<Integer, String> ETIQUETA_LINEA = Map.of(1, "carry", 2, "mid", 3, "offlane");

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
        CandidatosPorPosicion.Resultado candidatos = CandidatosPorPosicion.calcular(miRol, datos, props);
        if (candidatos.sinDatosDeLinea()) {
            advertencias.add("No hay datos de línea de OpenDota; las sugerencias de la posición " + miRol
                    + " se filtraron solo por las etiquetas de rol de los héroes, así que pueden incluir héroes "
                    + "que no suelen jugarse ahí.");
        }

        RendimientoJugador historial = personal;
        List<Sugerencia> sugerencias = candidatos.candidatos().stream()
                .filter(h -> !elegidos.contains(h.id()))
                .map(h -> puntuar(h, miRol, datos, enemigos, matchups, historial))
                .sorted(Comparator.comparingDouble(Sugerencia::puntaje).reversed()
                        .thenComparingInt(Sugerencia::heroId))
                .toList(); // ranking completo: ServicioSugerencias recorta tras repartir los repetidos
        advertencias.addAll(advertencias(datos, idsEnemigos, matchups));
        return new ResultadoSugerencias(sugerencias, advertencias);
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
    public static Set<Integer> oponentesDirectos(int posicion) {
        return switch (posicion) {
            case 1, 5 -> Set.of(3, 4);
            case 2 -> Set.of(2);
            case 3, 4 -> Set.of(1, 5);
            default -> Set.of();
        };
    }

    /** lane_role de OpenDota que corresponde a una posición núcleo (1-3); null para las posiciones 4-5. */
    static Integer lineaDe(int posicion) {
        return posicion >= 1 && posicion <= 3 ? posicion : null;
    }

    /**
     * Meta del héroe ajustado por posición: contrae sus partidas en la línea de {@code miRol} (si hay mapeo y
     * datos) hacia el meta global. Sin datos de línea equivale exactamente a {@code metaGlobal}. Público y estático
     * para que otros analizadores de draft (por ejemplo {@code AnalizadorHabilidad}, al desempatar candidatos)
     * reutilicen exactamente este cálculo en vez de repetirlo.
     */
    public static double metaPosicion(HeroeMeta heroe, int miRol, DatosMeta datos, double metaGlobal,
                                      SugerenciasProperties props) {
        Integer linea = lineaDe(miRol);
        if (linea == null) {
            return metaGlobal;
        }
        long partidasLinea = datos.partidasPorLinea().getOrDefault(linea, Map.of()).getOrDefault(heroe.id(), 0L);
        long victoriasLinea = datos.victoriasPorLinea().getOrDefault(linea, Map.of()).getOrDefault(heroe.id(), 0L);
        double k = props.fuerzaPriorLinea();
        return partidasLinea + k <= 0 ? metaGlobal : (victoriasLinea + k * metaGlobal) / (partidasLinea + k);
    }

    private Sugerencia puntuar(HeroeMeta heroe, int miRol, DatosMeta datos, Map<Integer, Integer> enemigos,
                               MatchupsEnemigos matchups, RendimientoJugador historial) {
        double metaGlobal = Wilson.limiteInferior(heroe.victorias(), heroe.partidas(), props.z());
        double metaPosicion = metaPosicion(heroe, miRol, datos, metaGlobal, props);
        Integer linea = lineaDe(miRol);
        long partidasLinea = linea == null ? 0
                : datos.partidasPorLinea().getOrDefault(linea, Map.of()).getOrDefault(heroe.id(), 0L);
        long victoriasLinea = linea == null ? 0
                : datos.victoriasPorLinea().getOrDefault(linea, Map.of()).getOrDefault(heroe.id(), 0L);
        Set<Integer> directos = oponentesDirectos(miRol);

        double sumaPonderada = 0;
        double sumaPesos = 0;
        List<Tendencia> tendencias = new ArrayList<>();
        List<String> sinDatos = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : enemigos.entrySet()) {
            boolean directo = directos.contains(e.getKey());
            double peso = directo ? props.pesoOponenteDirecto() : props.pesoOtrosEnemigos();
            HeroeMeta enemigo = datos.heroes().get(e.getValue());
            Duelo d = matchups.duelo(heroe.id(), e.getValue());
            boolean conMuestra = d != null && d.partidas() >= props.partidasMinimasDuelo();
            double estimacion = d == null || d.partidas() + props.fuerzaPrior() <= 0
                    ? metaPosicion
                    : (d.victorias() + props.fuerzaPrior() * metaPosicion) / (d.partidas() + props.fuerzaPrior());
            sumaPonderada += peso * estimacion;
            sumaPesos += peso;
            if (conMuestra) {
                double diferencia = estimacion - metaPosicion;
                if (enemigo != null && Math.abs(diferencia) >= props.ventajaMinimaDuelo()) {
                    tendencias.add(new Tendencia(enemigo.nombre(), diferencia, d.partidas()));
                }
            } else if (directo && enemigo != null) {
                sinDatos.add(enemigo.nombre());
            }
        }

        double puntaje = metaPosicion;
        if (sumaPesos > 0) {
            double counter = sumaPonderada / sumaPesos;
            puntaje = (props.pesoMeta() * metaPosicion + props.pesoContra() * counter)
                    / (props.pesoMeta() + props.pesoContra());
        }

        Duelo propio = null;
        if (historial != null) {
            propio = historial.de(heroe.id());
            double w = Math.max(0.0, Math.min(1.0, props.pesoPersonal()));
            double k = props.fuerzaPriorPersonal();
            double personal = propio == null || propio.partidas() + k <= 0
                    ? metaPosicion
                    : (propio.victorias() + k * metaPosicion) / (propio.partidas() + k);
            puntaje = (1 - w) * puntaje + w * personal;
        }
        boolean sinTendenciaRelevante = !enemigos.isEmpty() && tendencias.isEmpty();
        String motivo = motivo(heroe, linea, partidasLinea, victoriasLinea, tendencias, sinDatos,
                sinTendenciaRelevante, propio);
        return new Sugerencia(heroe.id(), heroe.nombre(), motivo, puntaje);
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

    private String motivo(HeroeMeta heroe, Integer linea, long partidasLinea, long victoriasLinea,
                          List<Tendencia> tendencias, List<String> sinDatos, boolean sinTendenciaRelevante,
                          Duelo propio) {
        StringBuilder sb = new StringBuilder();
        if (linea != null && partidasLinea > 0) {
            double winrateLinea = (double) victoriasLinea / partidasLinea;
            sb.append(String.format(Locale.ROOT, "%.1f%% de winrate como %s", winrateLinea * 100,
                    ETIQUETA_LINEA.get(linea)));
        } else {
            sb.append(String.format(Locale.ROOT, "%.1f%% en el meta", heroe.winrate() * 100));
        }
        List<Tendencia> mejores = tendencias.stream()
                .sorted(Comparator.comparingDouble((Tendencia t) -> Math.abs(t.diferencia())).reversed())
                .limit(MAX_TENDENCIAS_EN_MOTIVO)
                .toList();
        for (Tendencia t : mejores) {
            sb.append(String.format(Locale.ROOT, "; tendencia: %+.0f%% contra %s (%d partidas)",
                    t.diferencia() * 100, t.nombre(), t.partidas()));
        }
        if (sinTendenciaRelevante) {
            sb.append("; ranking según el meta, sin ventaja de duelo relevante");
        } else if (!sinDatos.isEmpty()) {
            sb.append("; sin datos suficientes contra ")
                    .append(String.join(" y ", sinDatos.stream().limit(MAX_TENDENCIAS_EN_MOTIVO).toList()));
        }
        if (propio != null && propio.partidas() >= props.minPartidasPersonal()) {
            sb.append(String.format(Locale.ROOT, "; tu winrate: %.0f%% en %d %s", propio.winrate() * 100,
                    propio.partidas(), propio.partidas() == 1 ? "partida" : "partidas"));
        }
        return sb.toString();
    }

    /** Un duelo citable en el motivo: su estimación contraída se aleja {@code diferencia} (con signo) del metaPosicion. */
    private record Tendencia(String nombre, double diferencia, int partidas) {
    }
}
