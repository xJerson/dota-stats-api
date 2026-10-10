package com.dotastats.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // TTL corto para no repetir llamadas a OpenDota en pedidos seguidos del mismo jugador
        // y así no agotar el límite de tasa gratuito (sin API key el límite es bajo y por IP).
        cacheManager.registerCustomCache("recursoJugador", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(2))
                .maximumSize(1000)
                .build());

        // Historial por héroe de un jugador (para personalizar sugerencias): cambia despacio, 30 min por cuenta.
        // Solo en memoria; los perfiles privados y los fallos lanzan excepción y por tanto no se cachean.
        cacheManager.registerCustomCache("rendimientoJugador", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(1000)
                .build());

        // (El catálogo de héroes se conserva en OpenDotaClientImpl, que decide cuándo cachear.)

        return cacheManager;
    }
}
