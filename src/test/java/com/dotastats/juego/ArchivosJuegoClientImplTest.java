package com.dotastats.juego;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Sin red: un servidor simulado sustituye a GitHub. */
class ArchivosJuegoClientImplTest {

    private static final String BASE = "http://juego.test/heroes";
    private static final String ARCHIVO = """
            "DOTAHeroes"
            {
              "npc_dota_hero_x"
              {
                "lion_voodoo"
                {
                  "AbilityBehavior" "DOTA_ABILITY_BEHAVIOR_UNIT_TARGET"
                  "SpellDispellableType" "SPELL_DISPELLABLE_YES_STRONG"
                }
              }
            }
            """;

    @Test
    void pideUnArchivoPorHeroeConSuNombreInternoYLoInterpreta() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(BASE + "/npc_dota_hero_lion.txt"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ARCHIVO, MediaType.TEXT_PLAIN));
        ArchivosJuegoClientImpl client = new ArchivosJuegoClientImpl(builder.build());

        Map<String, Map<String, IndicadoresHabilidad>> r = client.obtenerIndicadores(List.of("npc_dota_hero_lion"));

        assertThat(r).containsOnlyKeys("npc_dota_hero_lion");
        assertThat(r.get("npc_dota_hero_lion").get("lion_voodoo").disableDuro()).isTrue();
        servidor.verify();
    }

    @Test
    void unHeroeQueFallaSeSaltaYNoImpideLosDemas() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        servidor.expect(requestTo(BASE + "/npc_dota_hero_lion.txt")).andRespond(withSuccess(ARCHIVO, MediaType.TEXT_PLAIN));
        servidor.expect(requestTo(BASE + "/npc_dota_hero_antimage.txt")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        servidor.expect(requestTo(BASE + "/npc_dota_hero_sven.txt")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        servidor.expect(requestTo(BASE + "/npc_dota_hero_axe.txt")).andRespond(withSuccess(ARCHIVO, MediaType.TEXT_PLAIN));
        ArchivosJuegoClientImpl client = new ArchivosJuegoClientImpl(builder.build());

        Map<String, Map<String, IndicadoresHabilidad>> r = client.obtenerIndicadores(List.of(
                "npc_dota_hero_lion", "npc_dota_hero_antimage", "npc_dota_hero_sven", "npc_dota_hero_axe"));

        assertThat(r).containsOnlyKeys("npc_dota_hero_lion", "npc_dota_hero_axe");
        servidor.verify();
    }

    @Test
    void unTimeoutDeUnHeroeTambienSeSalta() {
        ClientHttpRequestFactory fabrica = (uri, metodo) -> new MockClientHttpRequest(metodo, uri) {
            @Override
            protected org.springframework.http.client.ClientHttpResponse executeInternal() throws IOException {
                if (uri.getPath().endsWith("sven.txt")) {
                    throw new SocketTimeoutException("Read timed out");
                }
                return respuestaOk();
            }
        };
        ArchivosJuegoClientImpl client = new ArchivosJuegoClientImpl(
                RestClient.builder().baseUrl(BASE).requestFactory(fabrica).build());

        Map<String, Map<String, IndicadoresHabilidad>> r = client.obtenerIndicadores(
                List.of("npc_dota_hero_sven", "npc_dota_hero_lion"));

        assertThat(r).containsOnlyKeys("npc_dota_hero_lion");
    }

    @Test
    void sinNombresNoHaceNingunaPeticion() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        ArchivosJuegoClientImpl client = new ArchivosJuegoClientImpl(builder.build());

        assertThat(client.obtenerIndicadores(List.of())).isEmpty();
        servidor.verify();
    }

    @Test
    void unArchivoSinHabilidadesLegiblesNoSeIncluye() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(BASE + "/npc_dota_hero_lion.txt"))
                .andRespond(withSuccess("<html>no es un archivo de KeyValues</html>", MediaType.TEXT_PLAIN));
        ArchivosJuegoClientImpl client = new ArchivosJuegoClientImpl(builder.build());

        assertThat(client.obtenerIndicadores(List.of("npc_dota_hero_lion"))).isEmpty();
    }

    @Test
    void nuncaHaceMasDeCincoPeticionesSimultaneas() {
        AtomicInteger enCurso = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        ClientHttpRequestFactory fabrica = (uri, metodo) -> new MockClientHttpRequest(metodo, uri) {
            @Override
            protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                maximo.accumulateAndGet(enCurso.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    enCurso.decrementAndGet();
                }
                return respuestaOk();
            }
        };
        ArchivosJuegoClientImpl client = new ArchivosJuegoClientImpl(
                RestClient.builder().baseUrl(BASE).requestFactory(fabrica).build());
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            nombres.add("npc_dota_hero_h" + i);
        }

        Map<String, Map<String, IndicadoresHabilidad>> r = client.obtenerIndicadores(nombres);

        assertThat(r).hasSize(30);
        assertThat(maximo.get()).isBetween(2, ArchivosJuegoClientImpl.MAX_DESCARGAS_PARALELAS);
    }


    @Test
    void alVencerElPlazoGlobalConservaLoQueYaLlegoYNoEsperaAlResto() {
        AtomicInteger lentosIniciados = new AtomicInteger();
        ClientHttpRequestFactory fabrica = (uri, metodo) -> new MockClientHttpRequest(metodo, uri) {
            @Override
            protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                if (uri.getPath().contains("lento")) {
                    lentosIniciados.incrementAndGet();
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("cancelado", e);
                    }
                }
                return respuestaOk();
            }
        };
        ArchivosJuegoClientImpl client = new ArchivosJuegoClientImpl(
                RestClient.builder().baseUrl(BASE).requestFactory(fabrica).build(), java.time.Duration.ofMillis(400));
        List<String> nombres = new ArrayList<>(List.of("npc_dota_hero_rapido1", "npc_dota_hero_rapido2"));
        for (int i = 0; i < 9; i++) {
            nombres.add("npc_dota_hero_lento" + i);
        }

        long inicio = System.nanoTime();
        Map<String, Map<String, IndicadoresHabilidad>> r = client.obtenerIndicadores(nombres);
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(r).containsOnlyKeys("npc_dota_hero_rapido1", "npc_dota_hero_rapido2");
        assertThat(ms).isLessThan(5_000);
        // Los que seguían en cola al vencer el plazo se cancelan: no llegan a pedirse.
        assertThat(lentosIniciados.get()).isLessThanOrEqualTo(ArchivosJuegoClientImpl.MAX_DESCARGAS_PARALELAS);
    }

    private static MockClientHttpResponse respuestaOk() {
        MockClientHttpResponse respuesta = new MockClientHttpResponse(ARCHIVO.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        respuesta.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        return respuesta;
    }
}
