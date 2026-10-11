package com.dotastats.client;

import com.dotastats.exception.JugadorNoEncontradoException;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class OpenDotaClientImpl implements OpenDotaClient {

    private static final Logger log = LoggerFactory.getLogger(OpenDotaClientImpl.class);

    private static final int MAX_INTENTOS = 4;
    private static final long ESPERA_BASE_MS = 3000;

    private final RestClient restClient;
    private volatile List<Map<String, Object>> catalogoHeroes;
    private volatile Map<String, Object> abilidades;
    private volatile Map<String, Object> habilidadesPorHeroe;
    private volatile Map<String, Object> heroesConstantes;

    public OpenDotaClientImpl(RestClient openDotaRestClient) {
        this.restClient = openDotaRestClient;
    }

    @Override
    @Cacheable("recursoJugador")
    public Object obtenerRecurso(long accountId, String subruta, Map<String, String> parametros) {
        String ruta = "/players/{accountId}" + subruta;
        return ejecutar(accountId, () -> restClient.get()
                .uri(uriBuilder -> {
                    parametros.forEach(uriBuilder::queryParam);
                    return uriBuilder.path(ruta).build(accountId);
                })
                .retrieve()
                .body(Object.class));
    }

    @Override
    public Object refrescar(long accountId) {
        return ejecutar(accountId, () -> restClient.post()
                .uri("/players/{accountId}/refresh", accountId)
                .retrieve()
                .body(Object.class));
    }

    /**
     * Catálogo de héroes (/heroes) con el nombre ACTUAL de cada uno: /heroes conserva nombres viejos
     * ("Outworld Destroyer") y /heroStats, que es de donde sale el meta, los nuevos ("Outworld Devourer"). Para que un
     * héroe se llame igual en el catálogo y en las sugerencias, {@code localized_name} se toma de /heroStats.
     * <p>
     * Se cachea en memoria sin expiración (cambia casi nunca) salvo que /heroStats falle: entonces se devuelve el
     * catálogo tal cual y no se guarda, para reintentar en la siguiente llamada.
     */
    @Override
    public List<Map<String, Object>> obtenerCatalogoHeroes() {
        List<Map<String, Object>> guardado = catalogoHeroes;
        if (guardado != null) {
            return guardado;
        }
        synchronized (this) {
            if (catalogoHeroes != null) {
                return catalogoHeroes;
            }
            List<Map<String, Object>> crudo = ejecutarSinJugador(() -> restClient.get()
                    .uri("/heroes")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    }));
            try {
                List<Map<String, Object>> fusionado = CatalogoHeroes.conNombresActuales(crudo, obtenerHeroStats());
                catalogoHeroes = fusionado;
                return fusionado;
            } catch (RuntimeException e) {
                log.warn("No se pudieron leer los nombres actuales de /heroStats; el catálogo usa los de /heroes: {}",
                        e.getMessage());
                return crudo;
            }
        }
    }

    // Sin @Cacheable: DatosMetaService conserva el snapshot en memoria y decide cuándo recargar.
    @Override
    public List<Map<String, Object>> obtenerHeroStats() {
        return ejecutarSinJugador(() -> restClient.get()
                .uri("/heroStats")
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }));
    }

    @Override
    public List<Map<String, Object>> obtenerMatchups(int heroId) {
        return ejecutarSinJugador(() -> restClient.get()
                .uri("/heroes/{heroId}/matchups", heroId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }));
    }

    // Sin @Cacheable: DatosMetaService las conserva dentro del snapshot de meta.
    @Override
    public List<Map<String, Object>> obtenerLaneRoles(int laneRole) {
        return ejecutarSinJugador(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/scenarios/laneRoles").queryParam("lane_role", laneRole).build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }));
    }

    // Sin @Cacheable: PerfilJugadorService cachea el resultado ya interpretado (y sin cachear perfiles privados).
    @Override
    public List<Map<String, Object>> obtenerHeroesJugador(long accountId) {
        return ejecutar(accountId, () -> restClient.get()
                .uri("/players/{accountId}/heroes", accountId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }));
    }

    @Override
    public Parche obtenerParche() {
        List<Map<String, Object>> parches = ejecutarSinJugador(() -> restClient.get()
                .uri("/constants/patch")
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }));
        if (parches == null || parches.isEmpty()) {
            throw new OpenDotaNoDisponibleException("OpenDota no devolvió la lista de parches");
        }
        Map<String, Object> ultimo = parches.stream()
                .max(Comparator.comparingInt(p -> ((Number) p.get("id")).intValue()))
                .orElseThrow();
        return new Parche(((Number) ultimo.get("id")).intValue(), String.valueOf(ultimo.get("name")));
    }

    /**
     * /constants/abilities, cacheada en memoria sin expiración (cambia solo con el parche, igual que el
     * catálogo de héroes): un fallo no se guarda, para reintentar en la siguiente llamada.
     */
    @Override
    public Map<String, Object> obtenerAbilidades() {
        Map<String, Object> guardado = abilidades;
        if (guardado != null) {
            return guardado;
        }
        synchronized (this) {
            if (abilidades != null) {
                return abilidades;
            }
            Map<String, Object> obtenidas = ejecutarSinJugador(() -> restClient.get()
                    .uri("/constants/abilities")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    }));
            abilidades = obtenidas;
            return obtenidas;
        }
    }

    /**
     * /constants/hero_abilities, cacheada en memoria sin expiración igual que {@link #obtenerAbilidades()}
     * (ambas cambian solo con el parche): un fallo no se guarda, para reintentar en la siguiente llamada.
     */
    @Override
    public Map<String, Object> obtenerHabilidadesPorHeroe() {
        Map<String, Object> guardado = habilidadesPorHeroe;
        if (guardado != null) {
            return guardado;
        }
        synchronized (this) {
            if (habilidadesPorHeroe != null) {
                return habilidadesPorHeroe;
            }
            Map<String, Object> obtenidas = ejecutarSinJugador(() -> restClient.get()
                    .uri("/constants/hero_abilities")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    }));
            habilidadesPorHeroe = obtenidas;
            return obtenidas;
        }
    }

    /**
     * /constants/heroes, cacheada en memoria sin expiración igual que {@link #obtenerAbilidades()}: un fallo no se
     * guarda, para reintentar en la siguiente llamada.
     */
    @Override
    public Map<String, Object> obtenerHeroesConstantes() {
        Map<String, Object> guardado = heroesConstantes;
        if (guardado != null) {
            return guardado;
        }
        synchronized (this) {
            if (heroesConstantes != null) {
                return heroesConstantes;
            }
            Map<String, Object> obtenidos = ejecutarSinJugador(() -> restClient.get()
                    .uri("/constants/heroes")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    }));
            heroesConstantes = obtenidos;
            return obtenidos;
        }
    }

    private <T> T ejecutar(long accountId, Supplier<T> llamada) {
        try {
            return conReintentos(llamada);
        } catch (HttpClientErrorException.NotFound e) {
            throw new JugadorNoEncontradoException(accountId);
        } catch (RestClientResponseException e) {
            throw traducirError(e);
        }
    }

    private <T> T ejecutarSinJugador(Supplier<T> llamada) {
        try {
            return conReintentos(llamada);
        } catch (RestClientResponseException e) {
            throw traducirError(e);
        }
    }

    /**
     * OpenDota sin API key limita a pocas peticiones por minuto y ese límite se comparte
     * con el resto del tráfico de la IP, así que un 429 puntual no significa que la cuota
     * siga agotada un segundo después. Reintentamos con espera creciente antes de rendirnos.
     */
    private <T> T conReintentos(Supplier<T> llamada) {
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                return llamada.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (intento == MAX_INTENTOS) {
                    log.warn("OpenDota siguió limitando tras {} intentos, se abandona", MAX_INTENTOS);
                    throw e;
                }
                long esperaMs = ESPERA_BASE_MS * intento;
                log.warn("OpenDota respondió 429 (intento {}/{}), reintentando en {}ms",
                        intento, MAX_INTENTOS, esperaMs);
                esperar(esperaMs);
            }
        }
        throw new IllegalStateException("No debería llegar aquí");
    }

    private void esperar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private OpenDotaNoDisponibleException traducirError(RestClientResponseException e) {
        if (e instanceof HttpClientErrorException.TooManyRequests) {
            return new OpenDotaNoDisponibleException(
                    "OpenDota está limitando las peticiones ahora mismo, intenta de nuevo en un momento");
        }
        HttpStatusCode status = e.getStatusCode();
        return new OpenDotaNoDisponibleException(
                "OpenDota respondió con un error inesperado (" + status.value() + ")");
    }
}
