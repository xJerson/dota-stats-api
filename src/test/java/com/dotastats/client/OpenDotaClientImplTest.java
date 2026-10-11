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

    @Test
    void pideLasAbilidadesYLasCacheaEnMemoria() {
        servidor.expect(requestTo("http://opendota.test/api/constants/abilities"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"lion_voodoo\":{\"dname\":\"Hex\",\"behavior\":\"Unit Target\"}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> primera = client.obtenerAbilidades();
        Map<String, Object> segunda = client.obtenerAbilidades(); // no repite la petición (está cacheada)

        assertThat(primera).containsKey("lion_voodoo");
        assertThat(segunda).isSameAs(primera);
        servidor.verify();
    }

    @Test
    void pideLasHabilidadesPorHeroeYLasCacheaEnMemoria() {
        servidor.expect(requestTo("http://opendota.test/api/constants/hero_abilities"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"npc_dota_hero_lion\":{\"abilities\":[\"lion_voodoo\"]}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> primera = client.obtenerHabilidadesPorHeroe();
        Map<String, Object> segunda = client.obtenerHabilidadesPorHeroe(); // no repite la petición (está cacheada)

        assertThat(primera).containsKey("npc_dota_hero_lion");
        assertThat(segunda).isSameAs(primera);
        servidor.verify();
    }

    @Test
    void pideLosHeroesConstantesYLosCacheaEnMemoria() {
        servidor.expect(requestTo("http://opendota.test/api/constants/heroes"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"17\":{\"id\":17,\"localized_name\":\"Storm Spirit\",\"attack_range\":480}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> primera = client.obtenerHeroesConstantes();
        Map<String, Object> segunda = client.obtenerHeroesConstantes(); // no repite la petición (está cacheada)

        assertThat(primera).containsKey("17");
        assertThat(segunda).isSameAs(primera);
        servidor.verify();
    }

    @Test
    void pideLasLineasPorRolYDevuelveLasFilasTalCualLasEntregaOpenDota() {
        servidor.expect(requestTo("http://opendota.test/api/scenarios/laneRoles?lane_role=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"hero_id\":1,\"lane_role\":2,\"time\":900,\"games\":\"5\",\"wins\":\"0\"}]",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> filas = client.obtenerLaneRoles(2);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0)).containsEntry("hero_id", 1).containsEntry("games", "5");
        servidor.verify();
    }
}
