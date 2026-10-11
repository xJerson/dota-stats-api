package com.dotastats.meta;

import com.dotastats.client.OpenDotaClient;
import com.dotastats.client.Parche;
import com.dotastats.exception.DatosNoCargadosException;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.juego.ArchivosJuegoClient;
import com.dotastats.juego.IndicadoresHabilidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mantiene en memoria los datos de meta con carga perezosa para gastar las mínimas peticiones a OpenDota.
 * <ul>
 *   <li>Base ({@link DatosMeta}): /heroStats, el parche y las partidas por línea (3 peticiones a
 *       /scenarios/laneRoles, opcionales: si fallan el meta se carga igual), cargados en segundo plano al arrancar (o leídos del
 *       archivo de caché en disco, sin tocar la red).</li>
 *   <li>Matchups: se piden {@code /heroes/{enemigo}/matchups} solo cuando un draft incluye a ese enemigo y aún no
 *       está en caché; peticiones simultáneas por el mismo héroe comparten una única llamada. Los fallos no se
 *       cachean.</li>
 *   <li>Los enemigos que faltan se descargan en paralelo con un pool propio de como máximo
 *       {@value #MAX_DESCARGAS_PARALELAS} hilos (compartido por todas las sugerencias, así que ese es también el tope global
 *       de peticiones de matchups simultáneas). Si {@code dotastats.meta.throttle-ms} es mayor que 0 se mantiene
 *       el modo serie con esa pausa entre peticiones, porque pausar y paralelizar son objetivos opuestos.</li>
 *   <li>Tras cada lote con descargas nuevas se persiste el snapshot en disco una sola vez. Si el parche vivo
 *       cambia, la siguiente sugerencia descarta todo y recarga la base.</li>
 * </ul>
 */
@Service
public class DatosMetaService {

    private static final Logger log = LoggerFactory.getLogger(DatosMetaService.class);

    /** Tope de peticiones de matchups simultáneas a OpenDota (un draft tiene como máximo 5 enemigos). */
    static final int MAX_DESCARGAS_PARALELAS = 5;

    private final OpenDotaClient client;
    private final ArchivosJuegoClient archivosJuego;
    private final MetaProperties props;
    private final CacheDisco cacheDisco;
    private final Executor ejecutor;
    /** Hilo propio del calentamiento de los archivos del juego, para no frenar la carga base ni un refresco. */
    private final Executor ejecutorJuego;
    private final Executor ejecutorMatchups = crearPoolMatchups();
    private final Clock reloj;

    private final AtomicBoolean cargando = new AtomicBoolean(false);
    private final Map<Integer, Map<Integer, Duelo>> matchups = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Map<Integer, Duelo>>> enVuelo = new ConcurrentHashMap<>();
    /** Protege el cambio de base: la generación sube cada vez que se vacía la caché y evita que un fetch viejo la ensucie. */
    private final Object cerrojo = new Object();
    /** Una sola descarga de las constantes de OpenDota a la vez. */
    private final Object cerrojoHabilidades = new Object();
    private final AtomicBoolean calentando = new AtomicBoolean(false);
    private volatile long versionIndicadores;
    private int generacion;

    private volatile DatosMeta datos;
    private volatile Map<String, Object> abilidades;
    private volatile Map<String, Object> habilidadesPorHeroe;
    private volatile Map<Integer, Integer> rangoAtaquePorHeroe;
    private volatile Map<String, Map<String, IndicadoresHabilidad>> indicadoresPorHeroe;
    private volatile boolean cacheDiscoUsada;
    private volatile String ultimoError;
    private volatile Parche parcheVivo;
    private volatile Instant parcheVivoEn;
    private volatile Instant noReintentarHasta;

    @Autowired
    public DatosMetaService(OpenDotaClient client, MetaProperties props, CacheDisco cacheDisco,
                            ArchivosJuegoClient archivosJuego) {
        this(client, props, cacheDisco, Executors.newSingleThreadExecutor(r -> {
            Thread hilo = new Thread(r, "carga-meta");
            hilo.setDaemon(true);
            return hilo;
        }), Clock.systemUTC(), archivosJuego, Executors.newSingleThreadExecutor(r -> {
            Thread hilo = new Thread(r, "archivos-juego-calentamiento");
            hilo.setDaemon(true);
            return hilo;
        }));
    }

    /** Sin archivos del juego (pruebas que no los necesitan): {@link ArchivosJuegoClient#NINGUNO}. */
    DatosMetaService(OpenDotaClient client, MetaProperties props, CacheDisco cacheDisco, Executor ejecutor, Clock reloj) {
        this(client, props, cacheDisco, ejecutor, reloj, ArchivosJuegoClient.NINGUNO, r -> { });
    }

    /** El calentamiento corre en el mismo ejecutor que la carga (pruebas con ejecutor en línea). */
    DatosMetaService(OpenDotaClient client, MetaProperties props, CacheDisco cacheDisco, Executor ejecutor, Clock reloj,
                     ArchivosJuegoClient archivosJuego) {
        this(client, props, cacheDisco, ejecutor, reloj, archivosJuego, ejecutor);
    }

    DatosMetaService(OpenDotaClient client, MetaProperties props, CacheDisco cacheDisco, Executor ejecutor, Clock reloj,
                     ArchivosJuegoClient archivosJuego, Executor ejecutorJuego) {
        this.ejecutorJuego = ejecutorJuego;
        this.client = client;
        this.props = props;
        this.cacheDisco = cacheDisco;
        this.ejecutor = ejecutor;
        this.reloj = reloj;
        this.archivosJuego = archivosJuego;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cargarAlIniciar() {
        if (props.cargarAlIniciar()) {
            lanzar(this::cargarInicial);
        }
    }

    /**
     * Vacía la caché (memoria y disco) y recarga /heroStats y el parche en segundo plano.
     *
     * @return false si ya había una carga en curso (no se arranca una segunda)
     */
    public boolean refrescar() {
        return lanzar(this::recargarDesdeRed);
    }

    /**
     * Datos de meta para puntuar un draft. Si el parche vivo ya no es el cargado, descarta la caché y recarga
     * antes de devolverlos; si esa recarga falla se siguen usando los datos anteriores.
     *
     * @throws DatosNoCargadosException si todavía no hay datos base
     */
    public DatosMeta datosParaSugerir() {
        if (datos == null) {
            throw new DatosNoCargadosException();
        }
        actualizarParcheVivo();
        if (esObsoleto(datos) && puedeReintentar() && cargando.compareAndSet(false, true)) {
            try {
                recargarDesdeRed();
            } finally {
                cargando.set(false);
            }
        }
        return datos;
    }

    /**
     * Matchups de los enemigos indicados, pidiendo a OpenDota solo los que no están en caché. Un enemigo cuya
     * petición falla queda en {@link MatchupsEnemigos#fallidos()} y no se cachea.
     *
     * @throws DatosNoCargadosException si todavía no hay datos base
     */
    public MatchupsEnemigos matchupsContra(List<Integer> enemigos) {
        if (datos == null) {
            throw new DatosNoCargadosException();
        }
        Map<Integer, Map<Integer, Duelo>> disponibles = new HashMap<>();
        Set<Integer> fallidos = new TreeSet<>();
        boolean descargoAlgo = props.throttleMs() > 0
                ? obtenerEnSerie(enemigos, disponibles, fallidos)
                : obtenerEnParalelo(enemigos, disponibles, fallidos);
        if (descargoAlgo) {
            persistir();
        }
        return new MatchupsEnemigos(disponibles, fallidos);
    }

    /** Modo con pausa de cortesía: una petición tras otra, esperando {@code throttleMs} entre dos descargas. */
    private boolean obtenerEnSerie(List<Integer> enemigos, Map<Integer, Map<Integer, Duelo>> disponibles,
                                   Set<Integer> fallidos) {
        boolean descargoAlgo = false;
        for (int enemigoId : new LinkedHashSet<>(enemigos)) {
            try {
                Obtenido o = obtener(enemigoId, descargoAlgo);
                disponibles.put(enemigoId, o.duelos());
                descargoAlgo |= o.descargado();
            } catch (RuntimeException e) {
                log.warn("No se pudieron obtener los matchups del héroe {}: {}", enemigoId, e.getMessage());
                fallidos.add(enemigoId);
            }
        }
        return descargoAlgo;
    }

    /** Lo ya cacheado se resuelve en el acto; lo que falta se pide en paralelo y se espera a que termine todo el lote. */
    private boolean obtenerEnParalelo(List<Integer> enemigos, Map<Integer, Map<Integer, Duelo>> disponibles,
                                      Set<Integer> fallidos) {
        Map<Integer, CompletableFuture<Obtenido>> pendientes = new LinkedHashMap<>();
        for (int enemigoId : new LinkedHashSet<>(enemigos)) {
            Map<Integer, Duelo> cacheado = matchups.get(enemigoId);
            if (cacheado != null) {
                disponibles.put(enemigoId, cacheado);
            } else {
                pendientes.put(enemigoId, CompletableFuture.supplyAsync(() -> obtener(enemigoId, false), ejecutorMatchups));
            }
        }
        boolean descargoAlgo = false;
        for (Map.Entry<Integer, CompletableFuture<Obtenido>> pendiente : pendientes.entrySet()) {
            try {
                Obtenido o = pendiente.getValue().join();
                disponibles.put(pendiente.getKey(), o.duelos());
                descargoAlgo |= o.descargado();
            } catch (CompletionException e) {
                Throwable causa = e.getCause() != null ? e.getCause() : e;
                log.warn("No se pudieron obtener los matchups del héroe {}: {}", pendiente.getKey(), causa.getMessage());
                fallidos.add(pendiente.getKey());
            }
        }
        return descargoAlgo;
    }

    /**
     * {@code /constants/abilities} y {@code /constants/hero_abilities}, para la fila de jugabilidad automática
     * (ver {@code CatalogoHabilidades}). Se piden a OpenDota solo la primera vez que se necesitan (NO al
     * arrancar), se cachean en memoria y disco junto al resto del snapshot, y se descartan cuando cambia el
     * parche (igual que los matchups: ver {@link #recargarDesdeRed()}). Un fallo no se cachea, para reintentar
     * en la siguiente llamada. La parte de OpenDota es de una sola descarga a la vez (un cerrojo propio).
     * <p>
     * Los indicadores de los archivos del juego (GitHub) NUNCA se piden aquí: los trae el calentamiento que
     * dispara la construcción del snapshot ({@link #calentarIndicadores()}); esta llamada devuelve los que haya en
     * ese momento (posiblemente ninguno o solo parte) y {@link #versionIndicadoresJuego()} avisa cuando cambian.
     *
     * @throws DatosNoCargadosException si todavía no hay datos base
     */
    public DatosHabilidad datosHabilidad() {
        if (datos == null) {
            throw new DatosNoCargadosException();
        }
        DatosHabilidad listo = leerHabilidadesSiEstanCompletas();
        if (listo != null) {
            return listo;
        }
        synchronized (cerrojoHabilidades) {
            listo = leerHabilidadesSiEstanCompletas();
            if (listo != null) {
                return listo;
            }
            Map<String, Object> actualAbilidades;
            Map<String, Object> actualHabilidadesPorHeroe;
            Map<Integer, Integer> actualRango;
            synchronized (cerrojo) {
                actualAbilidades = abilidades;
                actualHabilidadesPorHeroe = habilidadesPorHeroe;
                actualRango = rangoAtaquePorHeroe;
            }
            boolean pidioAlgo = false;
            if (actualAbilidades == null || actualHabilidadesPorHeroe == null) {
                Map<String, Object> nuevaAbilidades = client.obtenerAbilidades();
                Map<String, Object> nuevasHabilidadesPorHeroe = client.obtenerHabilidadesPorHeroe();
                if (nuevaAbilidades == null || nuevaAbilidades.isEmpty()
                        || nuevasHabilidadesPorHeroe == null || nuevasHabilidadesPorHeroe.isEmpty()) {
                    throw new OpenDotaNoDisponibleException(
                            "OpenDota no devolvió /constants/abilities o /constants/hero_abilities");
                }
                synchronized (cerrojo) {
                    abilidades = nuevaAbilidades;
                    habilidadesPorHeroe = nuevasHabilidadesPorHeroe;
                }
                actualAbilidades = nuevaAbilidades;
                actualHabilidadesPorHeroe = nuevasHabilidadesPorHeroe;
                pidioAlgo = true;
            }
            if (actualRango == null) {
                // El alcance de ataque solo alimenta la herramienta derivada RANGO_MAYOR: si falta no es un error, y
                // tampoco se cachea (se reintenta en la siguiente llamada, que solo hace la tabla una vez por snapshot).
                Map<Integer, Integer> nuevoRango = pedirAlcanceDeAtaque();
                if (!nuevoRango.isEmpty()) {
                    synchronized (cerrojo) {
                        rangoAtaquePorHeroe = nuevoRango;
                    }
                    pidioAlgo = true;
                }
                actualRango = nuevoRango;
            }
            if (pidioAlgo) {
                persistir();
            }
            Map<String, Map<String, IndicadoresHabilidad>> actualIndicadores;
            synchronized (cerrojo) {
                actualIndicadores = indicadoresPorHeroe;
            }
            return new DatosHabilidad(actualAbilidades, actualHabilidadesPorHeroe, actualRango,
                    actualIndicadores == null ? Map.of() : actualIndicadores);
        }
    }

    /** Lo que hay en memoria si OpenDota ya entregó todo (los indicadores del juego no cuentan); null si falta algo. */
    private DatosHabilidad leerHabilidadesSiEstanCompletas() {
        synchronized (cerrojo) {
            if (abilidades == null || habilidadesPorHeroe == null || rangoAtaquePorHeroe == null) {
                return null;
            }
            return new DatosHabilidad(abilidades, habilidadesPorHeroe, rangoAtaquePorHeroe,
                    indicadoresPorHeroe == null ? Map.of() : indicadoresPorHeroe);
        }
    }

    /**
     * Cambia cada vez que cambian los indicadores del juego (llegan, se descartan con el parche...). Es barato (sin
     * red ni copias): {@code TablaContrarrestos} lo usa para reconstruirse cuando llegan, en vez de quedarse para
     * siempre con la versión sin ellos.
     */
    public long versionIndicadoresJuego() {
        return versionIndicadores;
    }

    /**
     * Calienta los indicadores de los archivos del juego: pide (a GitHub, NUNCA dentro de una petición) los de los
     * héroes del snapshot que aún no se tienen. Lo dispara la construcción o lectura del snapshot, en su propio hilo.
     * <ul>
     *   <li>UNA descarga a la vez: una llamada con otra en curso vuelve sin descargar.</li>
     *   <li>Un resultado vacío no se guarda ni se da por final. Uno parcial se usa en memoria y se persiste tal cual:
     *       los héroes que faltan son los del snapshot menos los que hay, así que el siguiente calentamiento (al
     *       reiniciar o al reconstruir el snapshot) pide solo esos.</li>
     *   <li>Si el snapshot cambió mientras se descargaba (parche, refresco) el resultado se descarta y se repite
     *       para el snapshot nuevo.</li>
     * </ul>
     */
    void calentarIndicadores() {
        if (!calentando.compareAndSet(false, true)) {
            return;
        }
        try {
            for (int intento = 0; intento < 3 && calentarUnaVez(); intento++) {
                // calentarUnaVez devolvió true: el snapshot cambió a mitad y hay que repetir para el nuevo
                log.info("El snapshot cambió mientras se descargaban los archivos del juego; se repite para el nuevo");
            }
        } finally {
            calentando.set(false);
        }
    }

    /** @return true si hay que repetir porque el snapshot cambió durante la descarga */
    private boolean calentarUnaVez() {
        DatosMeta snapshot;
        int generacionAlPedir;
        Map<String, Map<String, IndicadoresHabilidad>> actuales;
        synchronized (cerrojo) {
            snapshot = datos;
            generacionAlPedir = generacion;
            actuales = indicadoresPorHeroe == null ? Map.of() : indicadoresPorHeroe;
        }
        if (snapshot == null) {
            return false;
        }
        List<String> faltantes = snapshot.heroes().values().stream()
                .map(HeroeMeta::nombreInterno)
                .filter(n -> n != null && !n.isBlank() && !actuales.containsKey(n))
                .sorted()
                .toList();
        if (faltantes.isEmpty()) {
            return false;
        }
        Map<String, Map<String, IndicadoresHabilidad>> obtenidos;
        try {
            obtenidos = archivosJuego.obtenerIndicadores(faltantes);
        } catch (RuntimeException e) {
            log.warn("No se pudieron obtener los archivos del juego; la fila de jugabilidad usa las interacciones por "
                    + "palabra clave hasta el próximo calentamiento: {}", e.getMessage());
            return false;
        }
        if (obtenidos == null || obtenidos.isEmpty()) {
            log.warn("No se obtuvo ningún archivo del juego; la fila de jugabilidad usa las interacciones por palabra "
                    + "clave hasta el próximo calentamiento");
            return false;
        }
        int total;
        synchronized (cerrojo) {
            if (generacion != generacionAlPedir) {
                return true;
            }
            Map<String, Map<String, IndicadoresHabilidad>> unidos = new HashMap<>(actuales);
            unidos.putAll(obtenidos);
            indicadoresPorHeroe = Map.copyOf(unidos);
            versionIndicadores++;
            total = unidos.size();
        }
        persistir();
        int esperados = actuales.size() + faltantes.size();
        if (total < esperados) {
            log.warn("Solo se tienen los archivos del juego de {} de {} héroes; los demás usan las interacciones por "
                    + "palabra clave hasta el próximo calentamiento", total, esperados);
        }
        return false;
    }

    /** Lanza el calentamiento en su propio hilo; nunca bloquea ni falla a quien lo pide. */
    private void lanzarCalentamiento() {
        try {
            ejecutorJuego.execute(this::calentarIndicadores);
        } catch (RuntimeException e) {
            log.warn("No se pudo lanzar la descarga de los archivos del juego: {}", e.getMessage());
        }
    }

    /** heroId -> {@code attack_range} base de {@code /constants/heroes}; vacío si OpenDota no responde o no lo trae. */
    private Map<Integer, Integer> pedirAlcanceDeAtaque() {
        try {
            Map<String, Object> crudo = client.obtenerHeroesConstantes();
            Map<Integer, Integer> rango = new HashMap<>();
            if (crudo != null) {
                for (Object valor : crudo.values()) {
                    if (valor instanceof Map<?, ?> heroe && heroe.get("id") instanceof Number id
                            && heroe.get("attack_range") instanceof Number alcance) {
                        rango.put(id.intValue(), alcance.intValue());
                    }
                }
            }
            return Map.copyOf(rango);
        } catch (RuntimeException e) {
            log.warn("No se pudo obtener /constants/heroes; la fila de jugabilidad queda sin la herramienta de "
                    + "alcance de ataque: {}", e.getMessage());
            return Map.of();
        }
    }

    public EstadoMeta estado() {
        DatosMeta actual = datos;
        if (actual != null && !cargando.get()) {
            actualizarParcheVivo();
        }
        Parche vivo = parcheVivo;
        List<Integer> conMatchups = matchups.keySet().stream().sorted().toList();
        return new EstadoMeta(
                actual != null,
                cargando.get(),
                actual == null ? null : actual.parche().nombre(),
                vivo == null ? null : vivo.nombre(),
                actual == null ? null : actual.cargadoEn(),
                esObsoleto(actual),
                conMatchups.size(),
                conMatchups,
                cacheDiscoUsada,
                ultimoError);
    }

    private boolean lanzar(Runnable tarea) {
        if (!cargando.compareAndSet(false, true)) {
            return false;
        }
        try {
            ejecutor.execute(() -> {
                try {
                    tarea.run();
                } finally {
                    cargando.set(false);
                }
            });
        } catch (RuntimeException e) {
            cargando.set(false);
            throw e;
        }
        return true;
    }

    private void cargarInicial() {
        Optional<SnapshotMeta> enDisco = cacheDisco.cargar();
        if (enDisco.isPresent()) {
            SnapshotMeta snapshot = enDisco.get();
            synchronized (cerrojo) {
                generacion++;
                matchups.clear();
                snapshot.matchups().forEach((id, contra) -> matchups.put(id, Map.copyOf(contra)));
                datos = snapshot.datos();
                abilidades = snapshot.abilidades().isEmpty() ? null : snapshot.abilidades();
                habilidadesPorHeroe = snapshot.habilidadesPorHeroe().isEmpty() ? null : snapshot.habilidadesPorHeroe();
                rangoAtaquePorHeroe = snapshot.rangoAtaquePorHeroe().isEmpty() ? null : snapshot.rangoAtaquePorHeroe();
                indicadoresPorHeroe = snapshot.indicadoresPorHeroe().isEmpty() ? null : snapshot.indicadoresPorHeroe();
                versionIndicadores++;
                cacheDiscoUsada = true;
            }
            ultimoError = null;
            log.info("Datos de meta leídos de la caché en disco: {} héroes, {} con matchups, parche {}",
                    snapshot.datos().heroes().size(), snapshot.matchups().size(), snapshot.datos().parche().nombre());
            lanzarCalentamiento(); // pide solo los héroes que la caché no trae (todos si es de antes de esta función)
            return;
        }
        recargarDesdeRed();
    }

    /** Pide parche y /heroStats; solo si ambos llegan reemplaza memoria y disco. Si fallan conserva lo que había. */
    private void recargarDesdeRed() {
        try {
            Parche parche = client.obtenerParche();
            List<Map<String, Object>> stats = client.obtenerHeroStats();
            if (parche == null || stats == null || stats.isEmpty()) {
                throw new OpenDotaNoDisponibleException("OpenDota no devolvió el parche o las estadísticas de héroes");
            }
            Map<Integer, HeroeMeta> heroes = new HashMap<>();
            for (Map<String, Object> s : stats) {
                HeroeMeta heroe = aHeroe(s);
                heroes.put(heroe.id(), heroe);
            }
            DatosLinea lineas = cargarLineas();
            DatosMeta nuevos = new DatosMeta(heroes, parche, reloj.instant(), lineas.partidas(), lineas.victorias());
            synchronized (cerrojo) {
                generacion++;
                matchups.clear();
                // Las habilidades no dependen del parche en teoría, pero se descartan igual que los matchups
                // para que una recarga (manual o por parche nuevo) también las refresque sin esperar un reinicio.
                abilidades = null;
                habilidadesPorHeroe = null;
                rangoAtaquePorHeroe = null;
                indicadoresPorHeroe = null;
                versionIndicadores++;
                datos = nuevos;
                cacheDiscoUsada = false;
            }
            parcheVivo = parche;
            parcheVivoEn = reloj.instant();
            ultimoError = null;
            noReintentarHasta = null;
            cacheDisco.borrar();
            persistir();
            log.info("Datos de meta cargados de OpenDota: {} héroes, parche {}", heroes.size(), parche.nombre());
            lanzarCalentamiento(); // en su propio hilo: ni esta carga ni ninguna petición esperan a GitHub
        } catch (RuntimeException e) {
            ultimoError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            noReintentarHasta = reloj.instant().plus(ttlParche());
            log.warn("Falló la carga de datos de meta: {}", ultimoError);
        }
    }

    /**
     * Partidas y victorias por héroe en las líneas 1-3 ({@code /scenarios/laneRoles}, una petición por línea). Si
     * cualquiera falla o llega vacía devuelve ambos mapas vacíos: el meta se carga igual y el motor recurre a las
     * etiquetas de rol (y al meta global en vez del meta por posición).
     */
    private DatosLinea cargarLineas() {
        Map<Integer, Map<Integer, Long>> partidas = new HashMap<>();
        Map<Integer, Map<Integer, Long>> victorias = new HashMap<>();
        try {
            for (int linea = 1; linea <= 3; linea++) {
                List<Map<String, Object>> filas = client.obtenerLaneRoles(linea);
                if (filas == null || filas.isEmpty()) {
                    throw new OpenDotaNoDisponibleException("OpenDota no devolvió datos de la línea " + linea);
                }
                Map<Integer, Long> porHeroePartidas = new HashMap<>();
                Map<Integer, Long> porHeroeVictorias = new HashMap<>();
                for (Map<String, Object> f : filas) {
                    int heroId = (int) numero(f.get("hero_id"));
                    porHeroePartidas.merge(heroId, numero(f.get("games")), Long::sum);
                    porHeroeVictorias.merge(heroId, numero(f.get("wins")), Long::sum);
                }
                partidas.put(linea, porHeroePartidas);
                victorias.put(linea, porHeroeVictorias);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron obtener las líneas de los héroes, se usarán las etiquetas de rol: {}", e.getMessage());
            return new DatosLinea(Map.of(), Map.of());
        }
        return new DatosLinea(partidas, victorias);
    }

    /** Resultado intermedio de {@link #cargarLineas()}: partidas y victorias por línea, con la misma forma. */
    private record DatosLinea(Map<Integer, Map<Integer, Long>> partidas, Map<Integer, Map<Integer, Long>> victorias) {
    }

    private Obtenido obtener(int enemigoId, boolean yaDescargoEnEstaLlamada) {
        Map<Integer, Duelo> cacheado = matchups.get(enemigoId);
        if (cacheado != null) {
            return new Obtenido(cacheado, false);
        }
        CompletableFuture<Map<Integer, Duelo>> mio = new CompletableFuture<>();
        CompletableFuture<Map<Integer, Duelo>> ajeno = enVuelo.putIfAbsent(enemigoId, mio);
        if (ajeno != null) {
            // Otro hilo ya lo está pidiendo: se espera su resultado (o su fallo, que join propaga).
            return new Obtenido(ajeno.join(), false);
        }
        try {
            // Quien terminó justo antes de que registráramos nuestro futuro ya dejó el resultado en caché.
            Map<Integer, Duelo> reciente = matchups.get(enemigoId);
            if (reciente != null) {
                mio.complete(reciente);
                return new Obtenido(reciente, false);
            }
            if (yaDescargoEnEstaLlamada) {
                pausar();
            }
            int generacionAlPedir = generacion();
            Map<Integer, Duelo> duelos = aDuelos(client.obtenerMatchups(enemigoId));
            synchronized (cerrojo) {
                // Si mientras tanto se descartó la caché (parche nuevo, refresh) este resultado ya no vale.
                if (generacionAlPedir == generacion) {
                    matchups.put(enemigoId, duelos);
                }
            }
            mio.complete(duelos);
            return new Obtenido(duelos, true);
        } catch (RuntimeException e) {
            mio.completeExceptionally(e);
            throw e;
        } finally {
            enVuelo.remove(enemigoId);
        }
    }

    private static Executor crearPoolMatchups() {
        AtomicInteger numero = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(MAX_DESCARGAS_PARALELAS, MAX_DESCARGAS_PARALELAS,
                30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread hilo = new Thread(r, "matchups-" + numero.incrementAndGet());
            hilo.setDaemon(true);
            return hilo;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private int generacion() {
        synchronized (cerrojo) {
            return generacion;
        }
    }

    private void persistir() {
        if (!cacheDisco.habilitada()) {
            return;
        }
        SnapshotMeta snapshot;
        synchronized (cerrojo) {
            if (datos == null) {
                return;
            }
            snapshot = new SnapshotMeta(SnapshotMeta.VERSION, datos, Map.copyOf(matchups),
                    abilidades == null ? Map.of() : abilidades,
                    habilidadesPorHeroe == null ? Map.of() : habilidadesPorHeroe,
                    rangoAtaquePorHeroe == null ? Map.of() : rangoAtaquePorHeroe,
                    indicadoresPorHeroe == null ? Map.of() : indicadoresPorHeroe);
        }
        cacheDisco.guardar(snapshot);
    }

    private void actualizarParcheVivo() {
        Instant ultimaConsulta = parcheVivoEn;
        if (ultimaConsulta != null && Duration.between(ultimaConsulta, reloj.instant()).compareTo(ttlParche()) < 0) {
            return;
        }
        try {
            Parche consultado = client.obtenerParche();
            if (consultado != null) {
                parcheVivo = consultado;
                parcheVivoEn = reloj.instant();
            }
        } catch (RuntimeException e) {
            // Sin poder consultar el parche no se afirma nada: se conserva lo último que se sabía.
            log.warn("No se pudo consultar el parche vivo: {}", e.getMessage());
        }
    }

    private boolean esObsoleto(DatosMeta actual) {
        Parche vivo = parcheVivo;
        return actual != null && vivo != null && vivo.id() != actual.parche().id();
    }

    /** Tras una recarga fallida no se vuelve a intentar en cada sugerencia, sino como mucho una vez por TTL. */
    private boolean puedeReintentar() {
        Instant hasta = noReintentarHasta;
        return hasta == null || !reloj.instant().isBefore(hasta);
    }

    private Duration ttlParche() {
        return Duration.ofMinutes(props.ttlParcheMinutos());
    }

    private HeroeMeta aHeroe(Map<String, Object> s) {
        long partidas = 0;
        long victorias = 0;
        for (int bracket : props.brackets()) {
            partidas += numero(s.get(bracket + "_pick"));
            victorias += numero(s.get(bracket + "_win"));
        }
        Set<String> roles = new HashSet<>();
        if (s.get("roles") instanceof List<?> lista) {
            lista.forEach(r -> roles.add(String.valueOf(r)));
        }
        Object nombreInterno = s.get("name");
        return new HeroeMeta(
                (int) numero(s.get("id")),
                String.valueOf(s.get("localized_name")),
                roles,
                partidas,
                victorias,
                nombreInterno == null ? null : String.valueOf(nombreInterno));
    }

    private Map<Integer, Duelo> aDuelos(List<Map<String, Object>> filas) {
        if (filas == null || filas.isEmpty()) {
            throw new OpenDotaNoDisponibleException("OpenDota no devolvió matchups");
        }
        Map<Integer, Duelo> duelos = new HashMap<>();
        for (Map<String, Object> f : filas) {
            duelos.put((int) numero(f.get("hero_id")),
                    new Duelo((int) numero(f.get("games_played")), (int) numero(f.get("wins"))));
        }
        return Map.copyOf(duelos);
    }

    /** Acepta números y cadenas numéricas (/scenarios/laneRoles entrega games y wins como cadenas). */
    private static long numero(Object valor) {
        if (valor instanceof Number n) {
            return n.longValue();
        }
        if (valor instanceof String texto) {
            try {
                return Long.parseLong(texto.trim());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    private void pausar() {
        if (props.throttleMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(props.throttleMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Petición de matchups interrumpida", e);
        }
    }

    private record Obtenido(Map<Integer, Duelo> duelos, boolean descargado) {
    }
}
