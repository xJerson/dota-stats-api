package com.dotastats.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    // Sin esto, una petición que se queda esperando respuesta de OpenDota
    // bloquea el hilo del request indefinidamente en vez de fallar rápido.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    // /heroStats pesa varios MB, por eso el margen de lectura es amplio.
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(15);

    @Bean
    public RestClient openDotaRestClient(
            @Value("${opendota.base-url}") String baseUrl,
            @Value("${opendota.api-key:}") String apiKey) {

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);

        if (StringUtils.hasText(apiKey)) {
            builder.requestInterceptor(new ApiKeyInterceptor(apiKey));
        }

        return builder.build();
    }
}
