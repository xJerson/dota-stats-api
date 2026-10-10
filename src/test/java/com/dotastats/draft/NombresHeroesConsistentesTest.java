package com.dotastats.draft;

import com.dotastats.client.OpenDotaClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Un mismo héroe debe llamarse igual en /api/draft/heroes (catálogo) y en lo que devuelve /api/draft/suggest
 * (nombres de /heroStats). OpenDota tiene nombres distintos en /heroes y /heroStats para algunos héroes renombrados.
 */
class NombresHeroesConsistentesTest {

    private static final String HEROES = """
            [{"id":76,"name":"npc_dota_hero_obsidian_destroyer","localized_name":"Outworld Destroyer","roles":["Carry"]},
             {"id":131,"name":"npc_dota_hero_ringmaster","localized_name":"Ringmaster","roles":["Support"]},
             {"id":1,"name":"npc_dota_hero_antimage","localized_name":"Anti-Mage","roles":["Carry"]}]""";
    private static final String HERO_STATS = """
            [{"id":76,"localized_name":"Outworld Devourer"},
             {"id":131,"localized_name":"Ring Master"},
             {"id":1,"localized_name":"Anti-Mage"}]""";

    private MockRestServiceServer servidor;
    private OpenDotaClientImpl client;
    private CatalogoHeroesService catalogo;
    private HeroeAliasResolver resolver;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://opendota.test/api");
        servidor = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        client = new OpenDotaClientImpl(builder.build());
        resolver = new HeroeAliasResolver(client);
        catalogo = new CatalogoHeroesService(client, resolver);
    }

    private void respondeOpenDota() {
        respondeOpenDota(once());
    }

    private void respondeOpenDota(ExpectedCount vecesHeroStats) {
        servidor.expect(once(), requestTo("http://opendota.test/api/heroes"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(HEROES, MediaType.APPLICATION_JSON));
        servidor.expect(vecesHeroStats, requestTo("http://opendota.test/api/heroStats"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(HERO_STATS, MediaType.APPLICATION_JSON));
    }

    @Test
    void elCatalogoYLasSugerenciasNombranIgualAlMismoHeroe() {
        respondeOpenDota(ExpectedCount.manyTimes()); // una vez para el catálogo y otra como lo lee el meta

        Map<Integer, String> delCatalogo = catalogo.listar().stream()
                .collect(Collectors.toMap(HeroeInfo::id, HeroeInfo::nombre));
        Map<Integer, String> delSuggest = client.obtenerHeroStats().stream()
                .collect(Collectors.toMap(h -> ((Number) h.get("id")).intValue(), h -> (String) h.get("localized_name")));

        assertThat(delCatalogo).isEqualTo(delSuggest);
        assertThat(delCatalogo.get(76)).isEqualTo("Outworld Devourer");
        assertThat(delCatalogo.get(131)).isEqualTo("Ring Master");
    }

    @Test
    void elCatalogoConservaLaImagenYLosRolesDelCatalogoOriginal() {
        respondeOpenDota();

        HeroeInfo od = catalogo.listar().stream().filter(h -> h.id() == 76).findFirst().orElseThrow();

        assertThat(od.img()).endsWith("/heroes/obsidian_destroyer.png");
        assertThat(od.roles()).containsExactly("Carry");
    }

    @Test
    void elResolverDevuelveElNombreActualYAceptaLaGrafiaAntiguaYLaNueva() {
        respondeOpenDota();

        for (String entrada : List.of("Outworld Devourer", "outworld destroyer", "OD", "od", "outworld")) {
            ResultadoAlias r = resolver.resolver(entrada);
            assertThat(r.tipo()).as(entrada).isEqualTo(ResultadoAlias.Tipo.RESUELTO);
            assertThat(r.heroId()).as(entrada).isEqualTo(76);
            assertThat(r.nombre()).as(entrada).isEqualTo("Outworld Devourer");
        }
        assertThat(resolver.resolver("ringmaster").nombre()).isEqualTo("Ring Master");
    }

    @Test
    void elCatalogoSeCacheaYNoRepiteLasPeticiones() {
        respondeOpenDota();

        catalogo.listar();
        catalogo.listar();
        resolver.resolver("od");

        servidor.verify();
    }

    @Test
    void siHeroStatsFallaSeUsaElCatalogoOriginalYSeReintentaLaVezSiguiente() {
        servidor.expect(once(), requestTo("http://opendota.test/api/heroes"))
                .andRespond(withSuccess(HEROES, MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo("http://opendota.test/api/heroStats"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY));
        assertThat(catalogo.listar()).extracting(HeroeInfo::nombre).contains("Outworld Destroyer");

        // La respuesta degradada no se queda para siempre: el siguiente intento vuelve a pedir heroStats.
        servidor.reset();
        respondeOpenDota();
        assertThat(catalogo.listar()).extracting(HeroeInfo::nombre)
                .contains("Outworld Devourer").doesNotContain("Outworld Destroyer");
    }
}
