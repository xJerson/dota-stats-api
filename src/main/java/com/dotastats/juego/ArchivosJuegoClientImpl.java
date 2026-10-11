package com.dotastats.juego;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cliente de los archivos del juego en {@code dotastats.juego.url-base} (por defecto el espejo {@code dotabuff/d2vpkr}
 * en GitHub). Mismos tiempos de espera por petición que el cliente de OpenDota (ver {@code RestClientConfig}); las
 * descargas se hacen en paralelo con un pool propio de como máximo {@value #MAX_DESCARGAS_PARALELAS} hilos y con un
 * PLAZO GLOBAL ({@code dotastats.juego.tiempo-maximo}, 60 s por defecto) para todo el lote.
 */
@Component
public class ArchivosJuegoClientImpl implements ArchivosJuegoClient {

    private static final Logger log = LoggerFactory.getLogger(ArchivosJuegoClientImpl.class);

    /** Tope de descargas simultáneas. */
    static final int MAX_DESCARGAS_PARALELAS = 5;

    private final RestClient restClient;
    private final Duration tiempoMaximo;
    private final ExecutorService pool = crearPool();

    @Autowired
    public ArchivosJuegoClientImpl(@Qualifier("juegoRestClient") RestClient juegoRestClient,
                                   @Value("${dotastats.juego.tiempo-maximo:60s}") Duration tiempoMaximo) {
        this.restClient = juegoRestClient;
        this.tiempoMaximo = tiempoMaximo;
    }

    /** Con el plazo global por defecto (60 s). */
    ArchivosJuegoClientImpl(RestClient juegoRestClient) {
        this(juegoRestClient, Duration.ofSeconds(60));
    }

    /**
     * Descarga todos los archivos con un plazo global: al vencer se queda con lo que ya llegó y cancela el resto (los
     * que seguían en cola ni siquiera se piden).
     */
    @Override
    public Map<String, Map<String, IndicadoresHabilidad>> obtenerIndicadores(Collection<String> nombresInternos) {
        Map<String, Future<Map<String, IndicadoresHabilidad>>> pendientes = new LinkedHashMap<>();
        for (String nombre : new LinkedHashSet<>(nombresInternos)) {
            pendientes.put(nombre, pool.submit(() -> descargar(nombre)));
        }
        long limite = System.nanoTime() + tiempoMaximo.toNanos();
        Map<String, Map<String, IndicadoresHabilidad>> resultado = new LinkedHashMap<>();
        boolean vencido = false;
        for (Map.Entry<String, Future<Map<String, IndicadoresHabilidad>>> e : pendientes.entrySet()) {
            Future<Map<String, IndicadoresHabilidad>> futuro = e.getValue();
            try {
                Map<String, IndicadoresHabilidad> indicadores;
                if (vencido) {
                    // Vencido el plazo solo se recoge lo que ya terminó; lo demás ya se canceló.
                    if (futuro.isCancelled()) {
                        continue;
                    }
                    indicadores = futuro.get();
                } else {
                    indicadores = futuro.get(Math.max(0L, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
                }
                if (!indicadores.isEmpty()) {
                    resultado.put(e.getKey(), indicadores);
                }
            } catch (TimeoutException ex) {
                vencido = true;
                // Se cancela TODO lo pendiente de una vez: si no, al liberarse un hilo tomaría el siguiente de la cola.
                pendientes.values().forEach(f -> f.cancel(true));
                log.warn("Se agotó el plazo de {} para descargar los archivos del juego; se conserva lo que llegó",
                        tiempoMaximo);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                pendientes.values().forEach(f -> f.cancel(true));
                break;
            } catch (ExecutionException | CancellationException ex) {
                log.warn("No se pudo obtener el archivo del juego de {}: {}", e.getKey(), ex.getMessage());
            }
        }
        return resultado;
    }

    /** Un héroe que falla se registra y devuelve vacío: nunca propaga. */
    private Map<String, IndicadoresHabilidad> descargar(String nombre) {
        try {
            String texto = restClient.get()
                    .uri("/{nombre}.txt", nombre)
                    .retrieve()
                    .body(String.class);
            Map<String, IndicadoresHabilidad> indicadores = LectorArchivoHeroe.leer(texto);
            if (indicadores.isEmpty()) {
                log.warn("El archivo del juego de {} no trajo ninguna habilidad legible; se omite", nombre);
            }
            return indicadores;
        } catch (RuntimeException e) {
            log.warn("No se pudo obtener el archivo del juego de {}; se omite: {}", nombre, e.getMessage());
            return Map.of();
        }
    }

    private static ExecutorService crearPool() {
        AtomicInteger numero = new AtomicInteger();
        ThreadPoolExecutor ejecutor = new ThreadPoolExecutor(MAX_DESCARGAS_PARALELAS, MAX_DESCARGAS_PARALELAS,
                30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread hilo = new Thread(r, "archivos-juego-" + numero.incrementAndGet());
            hilo.setDaemon(true);
            return hilo;
        });
        ejecutor.allowCoreThreadTimeOut(true);
        return ejecutor;
    }
}
