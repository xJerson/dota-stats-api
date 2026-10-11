package com.dotastats.analizadores.habilidad.dependencias;

import com.dotastats.analizadores.habilidad.HabilidadProperties;
import com.dotastats.client.Parche;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.HeroeMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositorioDependenciasTest {

    private static final String VALIDO = """
            {
              "dependencias": [
                {"heroe": "Storm Spirit", "dependencias": [
                  {"mecanismo": "ESCAPE", "habilidad": "storm_spirit_ball_lightning", "descripcion": "Se escapa con Ball Lightning."}
                ]},
                {"heroe": "Viper", "dependencias": [
                  {"mecanismo": "ACOSO_RANGO", "descripcion": "Acosa desde lejos."}
                ]}
              ],
              "herramientas": [
                {"heroe": "Lion", "herramientas": [
                  {"habilidad": "lion_voodoo", "efecto": "HEX", "claveDuracion": "duration", "confianza": "alta"},
                  {"habilidad": "lion_mana_drain", "efecto": "QUEMA_MANA", "claveEvidencia": "mana_per_second", "confianza": "media"}
                ]}
              ]
            }
            """;

    @TempDir
    Path tmp;

    private RepositorioDependencias repo(String json) throws IOException {
        Path archivo = tmp.resolve("dependencias.json");
        Files.writeString(archivo, json, StandardCharsets.UTF_8);
        HabilidadProperties props = new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null,
                new HabilidadProperties.Dependencias("file:" + archivo.toAbsolutePath(), 50));
        RepositorioDependencias repo = new RepositorioDependencias(new DefaultResourceLoader(), props);
        repo.cargar();
        return repo;
    }

    private static DatosMeta datos() {
        return new DatosMeta(Map.of(
                17, new HeroeMeta(17, "Storm Spirit", Set.of(), 1000, 500),
                47, new HeroeMeta(47, "Viper", Set.of(), 1000, 500),
                26, new HeroeMeta(26, "Lion", Set.of(), 1000, 500)),
                new Parche(60, "7.41"), Instant.parse("2026-10-10T12:00:00Z"));
    }

    private static String conHeroeDependencias(String cuerpoDependencia) {
        return "{\"dependencias\":[{\"heroe\":\"Storm Spirit\",\"dependencias\":[" + cuerpoDependencia + "]}],\"herramientas\":[]}";
    }

    private static String conHerramienta(String cuerpoHerramienta) {
        return "{\"dependencias\":[],\"herramientas\":[{\"heroe\":\"Lion\",\"herramientas\":[" + cuerpoHerramienta + "]}]}";
    }

    @Test
    void cargaElArchivoYLoIndexaPorIdDeHeroe() throws IOException {
        RepositorioDependencias repo = repo(VALIDO);

        RepositorioDependencias.Resueltas r = repo.resueltas(datos());

        assertThat(r.dependenciasPorHeroe()).containsOnlyKeys(17, 47);
        assertThat(r.dependenciasPorHeroe().get(17)).singleElement()
                .satisfies(d -> {
                    assertThat(d.mecanismo()).isEqualTo(Mecanismo.ESCAPE);
                    assertThat(d.habilidad()).isEqualTo("storm_spirit_ball_lightning");
                });
        assertThat(r.dependenciasPorHeroe().get(47).get(0).habilidad()).isNull();
        assertThat(r.herramientasPorHeroe()).containsOnlyKeys(26);
        assertThat(r.herramientasPorHeroe().get(26)).extracting(Herramienta::efecto)
                .containsExactly(Efecto.HEX, Efecto.QUEMA_MANA);
        assertThat(r.herramientasPorHeroe().get(26).get(0).esUlti()).isFalse();
    }

    @Test
    void sabeQueHeroesTienenDependenciasCuradas() throws IOException {
        RepositorioDependencias repo = repo(VALIDO);

        assertThat(repo.tieneDependencias(17, datos())).isTrue();
        assertThat(repo.tieneDependencias(26, datos())).isFalse(); // Lion solo tiene herramientas
        assertThat(repo.tieneDependencias(999, datos())).isFalse();
    }

    @Test
    void resuelveUnaSolaVezPorSnapshot() throws IOException {
        RepositorioDependencias repo = repo(VALIDO);
        DatosMeta datos = datos();

        assertThat(repo.resueltas(datos)).isSameAs(repo.resueltas(datos));
    }

    @Test
    void unNombreDeHeroeDesconocidoEnLasDependenciasSeSaltaYSeReportaSinLanzar() throws IOException {
        RepositorioDependencias repo = repo(VALIDO.replace("\"Viper\"", "\"Vyper\""));

        RepositorioDependencias.Resueltas r = repo.resueltas(datos());

        assertThat(r.dependenciasPorHeroe()).containsOnlyKeys(17);
        assertThat(r.problemas()).singleElement().asString().contains("Vyper").contains("dependencias");
        assertThat(repo.tieneDependencias(17, datos())).isTrue();
        assertThat(repo.tieneDependencias(47, datos())).isFalse();
    }

    @Test
    void unNombreDeHeroeDesconocidoEnLasHerramientasSeSaltaYSeReportaSinLanzar() throws IOException {
        RepositorioDependencias repo = repo(VALIDO.replace("\"Lion\"", "\"Leon\""));

        RepositorioDependencias.Resueltas r = repo.resueltas(datos());

        assertThat(r.herramientasPorHeroe()).isEmpty();
        assertThat(r.problemas()).singleElement().asString().contains("Leon").contains("herramientas");
    }

    @Test
    void faltaLaDescripcionDeUnaDependencia() {
        assertThatThrownBy(() -> repo(conHeroeDependencias("{\"mecanismo\":\"ESCAPE\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Storm Spirit")
                .hasMessageContaining("descripcion");
    }

    @Test
    void faltaElMecanismoDeUnaDependencia() {
        assertThatThrownBy(() -> repo(conHeroeDependencias("{\"descripcion\":\"x\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mecanismo");
    }

    @Test
    void unMecanismoDesconocidoEsUnErrorClaro() {
        assertThatThrownBy(() -> repo(conHeroeDependencias("{\"mecanismo\":\"TELEPORT\",\"descripcion\":\"x\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEPORT");
    }

    @Test
    void noSePermitenDosDependenciasConElMismoMecanismoEnUnHeroe() {
        String dos = "{\"mecanismo\":\"ESCAPE\",\"descripcion\":\"a\"},{\"mecanismo\":\"ESCAPE\",\"descripcion\":\"b\"}";

        assertThatThrownBy(() -> repo(conHeroeDependencias(dos)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ESCAPE");
    }

    @Test
    void unHeroeTieneComoMaximoTresDependencias() {
        String cuatro = "{\"mecanismo\":\"ESCAPE\",\"descripcion\":\"a\"},{\"mecanismo\":\"MANA\",\"descripcion\":\"b\"},"
                + "{\"mecanismo\":\"ILUSIONES\",\"descripcion\":\"c\"},{\"mecanismo\":\"PASIVAS\",\"descripcion\":\"d\"}";

        assertThatThrownBy(() -> repo(conHeroeDependencias(cuatro)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Storm Spirit")
                .hasMessageContaining("3");
    }

    @Test
    void unHeroeNoPuedeRepetirseEnLaListaDeDependencias() {
        String duplicado = "{\"dependencias\":[{\"heroe\":\"Viper\",\"dependencias\":[]},"
                + "{\"heroe\":\"Viper\",\"dependencias\":[]}],\"herramientas\":[]}";

        assertThatThrownBy(() -> repo(duplicado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Viper");
    }

    @Test
    void faltaLaHabilidadDeUnaHerramienta() {
        assertThatThrownBy(() -> repo(conHerramienta("{\"efecto\":\"HEX\",\"claveDuracion\":\"duration\",\"confianza\":\"alta\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Lion")
                .hasMessageContaining("habilidad");
    }

    @Test
    void laConfianzaDeUnaHerramientaDebeSerAltaMediaOBaja() {
        assertThatThrownBy(() -> repo(conHerramienta(
                "{\"habilidad\":\"lion_voodoo\",\"efecto\":\"HEX\",\"claveDuracion\":\"duration\",\"confianza\":\"segura\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confianza");
    }

    @Test
    void unEfectoDeDuracionExigeLaClaveDeDuracion() {
        assertThatThrownBy(() -> repo(conHerramienta(
                "{\"habilidad\":\"lion_voodoo\",\"efecto\":\"HEX\",\"claveEvidencia\":\"movespeed\",\"confianza\":\"alta\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claveDuracion");
    }

    @Test
    void unEfectoSinDuracionExigeAlMenosUnaClaveDeEvidenciaODuracion() {
        assertThatThrownBy(() -> repo(conHerramienta(
                "{\"habilidad\":\"zuus_lightning_bolt\",\"efecto\":\"REVELADO\",\"confianza\":\"alta\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claveEvidencia");
    }

    @Test
    void elRangoMayorSeDerivaDeLasEstadisticasYNoSeCura() {
        assertThatThrownBy(() -> repo(conHerramienta(
                "{\"habilidad\":\"lion_voodoo\",\"efecto\":\"RANGO_MAYOR\",\"claveEvidencia\":\"x\",\"confianza\":\"alta\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RANGO_MAYOR");
    }

    @Test
    void unEfectoDesconocidoEsUnErrorClaro() {
        assertThatThrownBy(() -> repo(conHerramienta(
                "{\"habilidad\":\"lion_voodoo\",\"efecto\":\"TELEPORTAR\",\"claveDuracion\":\"duration\",\"confianza\":\"alta\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEPORTAR");
    }

    @Test
    void unArchivoQueNoExisteFallaConUnMensajeClaro() {
        HabilidadProperties props = new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null,
                new HabilidadProperties.Dependencias("file:" + tmp.resolve("no-existe.json").toAbsolutePath(), 50));
        RepositorioDependencias repo = new RepositorioDependencias(new DefaultResourceLoader(), props);

        assertThatThrownBy(repo::cargar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dependencias");
    }

    @Test
    void elArchivoQueTraeElJarEsValido() {
        HabilidadProperties props = new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null, null);
        RepositorioDependencias repo = new RepositorioDependencias(new DefaultResourceLoader(), props);

        repo.cargar();

        assertThat(repo.todasLasDependencias()).extracting(DependenciasHeroe::heroe)
                .containsExactlyInAnyOrder("Storm Spirit", "Riki", "Phantom Lancer", "Huskar", "Viper");
        assertThat(repo.todasLasHerramientas().size()).isBetween(15, 40);
    }
}
