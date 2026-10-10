package com.dotastats.draft;

import com.dotastats.client.OpenDotaClient;
import com.dotastats.config.CacheConfig;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.exception.PerfilPrivadoException;
import com.dotastats.meta.Duelo;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Comprueba el cableado real de la caché por cuenta (proxy de Spring + Caffeine), sin red. */
@SpringJUnitConfig(PerfilJugadorCacheTest.Config.class)
class PerfilJugadorCacheTest {

    @Configuration
    @Import({CacheConfig.class, PerfilJugadorService.class})
    static class Config {
        @Bean
        OpenDotaClient openDotaClient() {
            return mock(OpenDotaClient.class);
        }
    }

    @Autowired
    private PerfilJugadorService servicio;

    @Autowired
    private OpenDotaClient client;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void elRendimientoDeUnaCuentaSeCacheaYNoVuelveAPedirseAOpenDota() {
        reset(client);
        when(client.obtenerHeroesJugador(100L)).thenReturn(List.of(PerfilJugadorServiceTest.fila(1, 10, 6)));

        RendimientoJugador a = servicio.obtener(100L);
        RendimientoJugador b = servicio.obtener(100L);

        assertThat(a.de(1)).isEqualTo(new Duelo(10, 6));
        assertThat(b).isSameAs(a);
        verify(client, times(1)).obtenerHeroesJugador(100L);
    }

    @Test
    void cuentasDistintasTienenEntradasDistintas() {
        reset(client);
        when(client.obtenerHeroesJugador(201L)).thenReturn(List.of(PerfilJugadorServiceTest.fila(1, 10, 6)));
        when(client.obtenerHeroesJugador(202L)).thenReturn(List.of(PerfilJugadorServiceTest.fila(1, 20, 5)));

        assertThat(servicio.obtener(201L).de(1)).isEqualTo(new Duelo(10, 6));
        assertThat(servicio.obtener(202L).de(1)).isEqualTo(new Duelo(20, 5));
    }

    @Test
    void unPerfilPrivadoNoSeCacheaParaPoderReintentarTrasActivarLosDatosPublicos() {
        reset(client);
        when(client.obtenerHeroesJugador(300L)).thenReturn(List.of(PerfilJugadorServiceTest.fila(1, 0, 0)));
        assertThatThrownBy(() -> servicio.obtener(300L)).isInstanceOf(PerfilPrivadoException.class);

        when(client.obtenerHeroesJugador(300L)).thenReturn(List.of(PerfilJugadorServiceTest.fila(1, 3, 2)));

        assertThat(servicio.obtener(300L).de(1)).isEqualTo(new Duelo(3, 2));
        verify(client, times(2)).obtenerHeroesJugador(300L);
    }

    @Test
    void unFalloDeOpenDotaNoSeCachea() {
        reset(client);
        when(client.obtenerHeroesJugador(400L)).thenThrow(new OpenDotaNoDisponibleException("caído"));
        assertThatThrownBy(() -> servicio.obtener(400L)).isInstanceOf(OpenDotaNoDisponibleException.class);

        doReturn(List.of(PerfilJugadorServiceTest.fila(1, 3, 2))).when(client).obtenerHeroesJugador(400L);

        assertThat(servicio.obtener(400L).de(1)).isEqualTo(new Duelo(3, 2));
    }

    @Test
    void laCacheExpiraA30MinutosDeEscritura() {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache("rendimientoJugador");
        Cache<Object, Object> nativa = cache.getNativeCache();

        assertThat(nativa.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(Duration.ofMinutes(30));
    }
}
