package com.dotastats.client;

import com.dotastats.exception.JugadorNoEncontradoException;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Sin red: un servidor simulado sustituye a OpenDota. */
class OpenDotaClientImplTest {

    private MockRestServiceServer servidor;
    private OpenDotaClientImpl client;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://opendota.test/api");
        servidor = MockRestServiceServer.bindTo(builder).build();
        client = new OpenDotaClientImpl(builder.build());
    }

    @Test
    void pideLosHeroesDelJugadorYDevuelveLasFilas() {
        servidor.expect(requestTo("http://opendota.test/api/players/42/heroes"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"hero_id\":1,\"games\":40,\"win\":26},{\"hero_id\":2,\"games\":0,\"win\":0}]",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> filas = client.obtenerHeroesJugador(42L);

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsEntry("hero_id", 1).containsEntry("games", 40).containsEntry("win", 26);
        servidor.verify();
    }

    @Test
    void unNotFoundSeTraduceAJugadorNoEncontrado() {
        servidor.expect(requestTo("http://opendota.test/api/players/42/heroes"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.obtenerHeroesJugador(42L)).isInstanceOf(JugadorNoEncontradoException.class);
    }

    @Test
    void unErrorDelServidorSeTraduceAOpenDotaNoDisponible() {
        servidor.expect(requestTo("http://opendota.test/api/players/42/heroes"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.obtenerHeroesJugador(42L)).isInstanceOf(OpenDotaNoDisponibleException.class);
    }
}
