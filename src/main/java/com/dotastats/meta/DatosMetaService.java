package com.dotastats.meta;

import com.dotastats.client.OpenDotaClient;
import com.dotastats.client.Parche;
import com.dotastats.exception.DatosNoCargadosException;
import com.dotastats.exception.OpenDotaNoDisponibleException;
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
    private final MetaProperties props;
    private final CacheDisco cacheDisco;
    private final Executor ejecutor;
    private final Executor ejecutorMatchups = crearPoolMatchups();
    private final Clock reloj;

    private final AtomicBoolean cargando = new AtomicBoolean(false);
    private final Map<Integer, Map<Integer, Duelo>> matchups = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Map<Integer, Duelo>>> enVuelo = new ConcurrentHashMap<>();
    /** Protege el cambio de base: la generación sube cada vez que se vacía la caché y evita que un fetch viejo la ensucie. */
    private final Object cerrojo = new Object();
    private int generacion;

    private volatile DatosMeta datos;
    private volatile boolean cacheDiscoUsada;
    private volatile String ultimoError;
    private volatile Parche parcheVivo;
    private volatile Instant parcheVivoEn;
    private volatile Instant noReintentarHasta;

    @Autowired
    public DatosMetaService(OpenDotaClient client, MetaProperties props, CacheDisco cacheDisco) {
        this(client, props, cacheDisco, Executors.newSingleThreadExecutor(r -> {
            Thread hilo = new Thread(r, "carga-meta");
            hilo.setDaemon(true);
            return hilo;
        }), Clock.systemUTC());
    }

    DatosMetaService(OpenDotaClient client, MetaProperties props, CacheDisco cacheDisco, Executor ejecutor, Clock reloj) {
        this.client = client;
        this.props = props;
        this.cacheDisco = cacheDisco;
        this.ejecutor = ejecutor;
        this.reloj = reloj;
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
                cacheDiscoUsada = true;
            }
            ultimoError = null;
            log.info("Datos de meta leídos de la caché en disco: {} héroes, {} con matchups, parche {}",
                    snapshot.datos().heroes().size(), snapshot.matchups().size(), snapshot.datos().parche().nombre());
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
            DatosMeta nuevos = new DatosMeta(heroes, parche, reloj.instant(), cargarLineas());
            synchronized (cerrojo) {
                generacion++;
                matchups.clear();
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
        } catch (RuntimeException e) {
            ultimoError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            noReintentarHasta = reloj.instant().plus(ttlParche());
            log.warn("Falló la carga de datos de meta: {}", ultimoError);
        }
    }

    /**
     * Partidas por héroe en las líneas 1-3 ({@code /scenarios/laneRoles}, una petición por línea). Si cualquiera falla
     * o llega vacía devuelve un mapa vacío: el meta se carga igual y el motor recurre a las etiquetas de rol.
     */
    private Map<Integer, Map<Integer, Long>> cargarLineas() {
        Map<Integer, Map<Integer, Long>> lineas = new HashMap<>();
        try {
            for (int linea = 1; linea <= 3; linea++) {
                List<Map<String, Object>> filas = client.obtenerLaneRoles(linea);
                if (filas == null || filas.isEmpty()) {
                    throw new OpenDotaNoDisponibleException("OpenDota no devolvió datos de la línea " + linea);
                }
                Map<Integer, Long> porHeroe = new HashMap<>();
                for (Map<String, Object> f : filas) {
                    porHeroe.merge((int) numero(f.get("hero_id")), numero(f.get("games")), Long::sum);
                }
                lineas.put(linea, porHeroe);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron obtener las líneas de los héroes, se usarán las etiquetas de rol: {}", e.getMessage());
            return Map.of();
        }
        return lineas;
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
            snapshot = new SnapshotMeta(SnapshotMeta.VERSION, datos, Map.copyOf(matchups));
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
        return new HeroeMeta(
                (int) numero(s.get("id")),
                String.valueOf(s.get("localized_name")),
                roles,
                partidas,
                victorias);
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
