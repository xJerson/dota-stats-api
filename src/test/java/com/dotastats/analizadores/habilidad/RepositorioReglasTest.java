package com.dotastats.analizadores.habilidad;

import com.dotastats.client.Parche;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.HeroeMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Nunca toca la red: el JSON vive en un archivo temporal y el catálogo es un {@link DatosMeta} de prueba. */
class RepositorioReglasTest {

    @TempDir
    Path tmp;

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    private RepositorioReglas repoCon(String json) throws IOException {
        Path archivo = tmp.resolve("reglas.json");
        Files.writeString(archivo, json);
        HabilidadProperties props = new HabilidadProperties(1.0, 0.5, 0.25, "file:" + archivo.toAbsolutePath(), null, null);
        RepositorioReglas repo = new RepositorioReglas(resourceLoader, props);
        invocarCargar(repo);
        return repo;
    }

    private static void invocarCargar(RepositorioReglas repo) {
        try {
            var m = RepositorioReglas.class.getDeclaredMethod("cargar");
            m.setAccessible(true);
            m.invoke(repo);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static DatosMeta datosCon(Map<Integer, String> heroes) {
        Map<Integer, HeroeMeta> m = new java.util.HashMap<>();
        heroes.forEach((id, nombre) -> m.put(id, new HeroeMeta(id, nombre, Set.of(), 1000, 500)));
        return new DatosMeta(m, new Parche(60, "7.41"), Instant.parse("2026-10-10T12:00:00Z"));
    }

    private static final String UNA_REGLA_VALIDA = """
            [{"id":"r1","enemigos":["Storm Spirit"],"rasgo":"escape por habilidad","counter":"Lion",
              "habilidad":"lion_voodoo","habilidadNombre":"Hex","anula":["storm_spirit_ball_lightning"],
              "anulaEsUlti":true,"esUlti":false,"confianza":"alta","motivo":"Hex lo inmoviliza aunque use Ball Lightning."}]
            """;

    @Test
    void cargaUnaReglaValidaYLaExponeEnTodas() throws IOException {
        RepositorioReglas repo = repoCon(UNA_REGLA_VALIDA);

        assertThat(repo.todas()).hasSize(1);
        ReglaHabilidad r = repo.todas().get(0);
        assertThat(r.id()).isEqualTo("r1");
        assertThat(r.enemigos()).containsExactly("Storm Spirit");
        assertThat(r.counter()).isEqualTo("Lion");
        assertThat(r.anula()).containsExactly("storm_spirit_ball_lightning");
        assertThat(r.anulaEsUlti()).isTrue();
        assertThat(r.confianza()).isEqualTo("alta");
    }

    @Test
    void indexaLaReglaPorElHeroIdDelEnemigoUnaVezResueltaContraElCatalogo() throws IOException {
        RepositorioReglas repo = repoCon(UNA_REGLA_VALIDA);
        DatosMeta datos = datosCon(Map.of(26, "Lion", 6, "Storm Spirit"));

        List<RepositorioReglas.ReglaResuelta> porStorm = repo.reglasPara(6, datos);

        assertThat(porStorm).hasSize(1);
        assertThat(porStorm.get(0).counterHeroId()).isEqualTo(26);
        assertThat(porStorm.get(0).regla().id()).isEqualTo("r1");
    }

    @Test
    void unEnemigoSinReglasDevuelveListaVacia() throws IOException {
        RepositorioReglas repo = repoCon(UNA_REGLA_VALIDA);
        DatosMeta datos = datosCon(Map.of(26, "Lion", 6, "Storm Spirit", 9, "Otro"));

        assertThat(repo.reglasPara(9, datos)).isEmpty();
    }

    @Test
    void unaReglaConDosEnemigosSeIndexaParaCadaUno() throws IOException {
        String json = """
                [{"id":"r15","enemigos":["Lion","Shadow Shaman"],"rasgo":"control","counter":"Abaddon",
                  "habilidad":"abaddon_aphotic_shield","habilidadNombre":"Aphotic Shield",
                  "anula":["lion_voodoo","shadow_shaman_voodoo"],"anulaEsUlti":false,"esUlti":false,
                  "confianza":"media","motivo":"Aphotic Shield te quita el Hex."}]
                """;
        RepositorioReglas repo = repoCon(json);
        DatosMeta datos = datosCon(Map.of(26, "Lion", 27, "Shadow Shaman", 102, "Abaddon"));

        assertThat(repo.reglasPara(26, datos)).hasSize(1);
        assertThat(repo.reglasPara(27, datos)).hasSize(1);
        assertThat(repo.reglasPara(26, datos).get(0).counterHeroId()).isEqualTo(102);
    }

    @Test
    void unHeroeDesconocidoComoCounterFallaAlResolver() throws IOException {
        RepositorioReglas repo = repoCon(UNA_REGLA_VALIDA);
        DatosMeta datos = datosCon(Map.of(6, "Storm Spirit")); // falta Lion

        assertThatThrownBy(() -> repo.reglasPara(6, datos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("r1")
                .hasMessageContaining("Lion");
    }

    @Test
    void unHeroeDesconocidoComoEnemigoFallaAlResolver() throws IOException {
        RepositorioReglas repo = repoCon(UNA_REGLA_VALIDA);
        DatosMeta datos = datosCon(Map.of(26, "Lion")); // falta Storm Spirit

        assertThatThrownBy(() -> repo.reglasPara(26, datos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("r1")
                .hasMessageContaining("Storm Spirit");
    }

    @Test
    void faltaUnCampoObligatorioFallaAlCargar() {
        String sinMotivo = """
                [{"id":"r1","enemigos":["Storm Spirit"],"counter":"Lion","habilidad":"lion_voodoo",
                  "habilidadNombre":"Hex","confianza":"alta","motivo":""}]
                """;

        assertThatThrownBy(() -> repoCon(sinMotivo))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void unaConfianzaInvalidaFallaAlCargar() {
        String confianzaMala = """
                [{"id":"r1","enemigos":["Storm Spirit"],"counter":"Lion","habilidad":"lion_voodoo",
                  "habilidadNombre":"Hex","confianza":"maxima","motivo":"texto"}]
                """;

        assertThatThrownBy(() -> repoCon(confianzaMala))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void idsDuplicadosFallanAlCargar() {
        String duplicada = """
                [{"id":"r1","enemigos":["Storm Spirit"],"counter":"Lion","habilidad":"lion_voodoo",
                  "habilidadNombre":"Hex","confianza":"alta","motivo":"texto"},
                 {"id":"r1","enemigos":["Puck"],"counter":"Death Prophet","habilidad":"death_prophet_silence",
                  "habilidadNombre":"Silence","confianza":"media","motivo":"otro texto"}]
                """;

        assertThatThrownBy(() -> repoCon(duplicada))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Regla de habilidad inválida (r1): id duplicado: r1");
    }

    @Test
    void elArchivoRealDelProyectoCargaYResuelveSinErrores() {
        HabilidadProperties props = new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null, null);
        RepositorioReglas repo = new RepositorioReglas(resourceLoader, props);
        invocarCargar(repo);

        assertThat(repo.todas()).hasSize(16);
    }
}
