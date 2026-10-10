package com.dotastats.config;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

/**
 * Agrega ?api_key=... a cada petición saliente a OpenDota.
 * Sin key, OpenDota aplica un límite de tasa muy bajo compartido por IP;
 * con key el límite sube bastante y se elimina el tope mensual.
 * https://docs.opendota.com/#section/Introduction
 */
public class ApiKeyInterceptor implements ClientHttpRequestInterceptor {

    private final String apiKey;

    public ApiKeyInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        URI uriConKey = UriComponentsBuilder.fromUri(request.getURI())
                .queryParam("api_key", apiKey)
                .build(true)
                .toUri();

        HttpRequest wrapped = new HttpRequestWrapper(request, uriConKey);
        return execution.execute(wrapped, body);
    }

    private record HttpRequestWrapper(HttpRequest delegate, URI uri) implements HttpRequest {
        @Override
        public HttpMethod getMethod() {
            return delegate.getMethod();
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}
