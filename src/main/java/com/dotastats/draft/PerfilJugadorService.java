package com.dotastats.draft;

import com.dotastats.client.OpenDotaClient;
import com.dotastats.exception.JugadorNoEncontradoException;
import com.dotastats.exception.PerfilPrivadoException;
import com.dotastats.meta.Duelo;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Obtiene el historial por héroe de un jugador desde OpenDota ({@code /players/{id}/heroes}).
 * <p>
 * Cacheado en memoria por cuenta (cache {@code rendimientoJugador}, 30 min; ver CacheConfig) y nunca en disco.
 * Las excepciones no se cachean, así que un perfil privado o un fallo de OpenDota se reintentan en la
 * siguiente petición.
 * <p>
 * Detección de perfil privado: para una cuenta sin datos públicos (o inexistente) OpenDota NO devuelve un
 * error, responde 200 con una fila por héroe (~127) y todas con {@code games = 0}. Por eso "perfil privado o sin
 * partidas públicas" se define como: la lista llega vacía/nula o la suma de partidas de todos los héroes es 0.
 */
@Service
public class PerfilJugadorService {

    private final OpenDotaClient client;

    public PerfilJugadorService(OpenDotaClient client) {
        this.client = client;
    }

    /**
     * @throws PerfilPrivadoException              si la cuenta no tiene partidas públicas
     * @throws com.dotastats.exception.OpenDotaNoDisponibleException si OpenDota no responde
     */
    @Cacheable("rendimientoJugador")
    public RendimientoJugador obtener(long accountId) {
        List<Map<String, Object>> filas;
        try {
            filas = client.obtenerHeroesJugador(accountId);
        } catch (JugadorNoEncontradoException e) {
            throw new PerfilPrivadoException();
        }
        Map<Integer, Duelo> porHeroe = new HashMap<>();
        if (filas != null) {
            for (Map<String, Object> f : filas) {
                if (!(f.get("hero_id") instanceof Number heroId)) {
                    continue;
                }
                long partidas = numero(f.get("games"));
                if (partidas > 0) {
                    porHeroe.put(heroId.intValue(), new Duelo((int) partidas, (int) numero(f.get("win"))));
                }
            }
        }
        if (porHeroe.isEmpty()) {
            throw new PerfilPrivadoException();
        }
        return new RendimientoJugador(porHeroe);
    }

    private static long numero(Object valor) {
        return valor instanceof Number n ? n.longValue() : 0L;
    }
}
