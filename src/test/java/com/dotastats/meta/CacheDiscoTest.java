package com.dotastats.meta;

import com.dotastats.client.Parche;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CacheDiscoTest {

    @TempDir
    Path dir;

    private static SnapshotMeta snapshot() {
        DatosMeta datos = new DatosMeta(
                Map.of(1, new HeroeMeta(1, "Alfa", Set.of("Carry", "Nuker"), 1500, 780)),
                new Parche(60, "7.41"),
                Instant.parse("2026-10-10T12:00:00Z"),
                Map.of(1, Map.of(1, 700L), 2, Map.of(1, 200L), 3, Map.of(1, 100L)),
                Map.of(1, Map.of(1, 400L), 2, Map.of(1, 100L), 3, Map.of(1, 50L)));
        return new SnapshotMeta(SnapshotMeta.VERSION, datos, Map.of(1, Map.of(2, new Duelo(90, 50))),
                Map.of("lion_voodoo", Map.of("dname", "Hex")),
                Map.of("npc_dota_hero_lion", Map.of("abilities", List.of("lion_voodoo"))));
    }

    @Test
    void guardaYRecuperaElSnapshotCompleto() {
        CacheDisco cache = new CacheDisco(dir.resolve("meta-cache.json"));

        cache.guardar(snapshot());
        Optional<SnapshotMeta> leido = cache.cargar();

        assertThat(leido).isPresent();
        assertThat(leido.get().datos().parche()).isEqualTo(new Parche(60, "7.41"));
        assertThat(leido.get().datos().cargadoEn()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));
        assertThat(leido.get().datos().heroes().get(1).roles()).containsExactlyInAnyOrder("Carry", "Nuker");
        assertThat(leido.get().datos().heroes().get(1).victorias()).isEqualTo(780);
        assertThat(leido.get().matchups().get(1).get(2)).isEqualTo(new Duelo(90, 50));
        assertThat(leido.get().datos().partidasPorLinea().get(2).get(1)).isEqualTo(200L);
        assertThat(leido.get().datos().victoriasPorLinea().get(2).get(1)).isEqualTo(100L);
        assertThat(leido.get().abilidades()).containsKey("lion_voodoo");
        assertThat(leido.get().habilidadesPorHeroe()).containsKey("npc_dota_hero_lion");
    }

    @Test
    void guardaYRecuperaElAlcanceDeAtaquePorHeroe() {
        CacheDisco cache = new CacheDisco(dir.resolve("meta-cache.json"));
        SnapshotMeta base = snapshot();

        cache.guardar(new SnapshotMeta(SnapshotMeta.VERSION, base.datos(), base.matchups(), base.abilidades(),
                base.habilidadesPorHeroe(), Map.of(1, 575)));

        assertThat(cache.cargar().orElseThrow().rangoAtaquePorHeroe()).containsExactly(Map.entry(1, 575));
    }

    @Test
    void unaCacheSinAlcanceDeAtaqueSeLeeConElAlcanceVacioEnVezDeIgnorarse() {
        CacheDisco cache = new CacheDisco(dir.resolve("meta-cache.json"));
        cache.guardar(snapshot()); // el constructor de 5 campos no trae alcance

        Optional<SnapshotMeta> leido = cache.cargar();

        assertThat(leido).isPresent();
        assertThat(leido.get().rangoAtaquePorHeroe()).isEmpty();
        assertThat(leido.get().abilidades()).containsKey("lion_voodoo");
    }

    @Test
    void unaCacheDeVersionAnteriorSinDatosDeLineaSeIgnoraEnVezDeFallar() throws IOException {
        Path archivo = dir.resolve("meta-cache.json");
        Files.writeString(archivo, """
                {"version":1,"datos":{"heroes":{"1":{"id":1,"nombre":"Alfa","roles":["Carry"],"partidas":10,"victorias":5}},
                "parche":{"id":60,"nombre":"7.41"},"cargadoEn":"2026-10-10T12:00:00Z"},"matchups":{}}
                """);

        assertThat(new CacheDisco(archivo).cargar()).isEmpty();
    }

    @Test
    void sinArchivoNoDevuelveNada() {
        assertThat(new CacheDisco(dir.resolve("no-existe.json")).cargar()).isEmpty();
    }

    @Test
    void archivoCorruptoSeIgnoraSinLanzarExcepcion() throws IOException {
        Path archivo = dir.resolve("meta-cache.json");
        Files.writeString(archivo, "{esto no es json");

        assertThat(new CacheDisco(archivo).cargar()).isEmpty();
    }

    @Test
    void archivoValidoPeroIncompletoSeIgnora() throws IOException {
        Path archivo = dir.resolve("meta-cache.json");
        Files.writeString(archivo, "{\"version\":1}");

        assertThat(new CacheDisco(archivo).cargar()).isEmpty();
    }

    @Test
    void versionDistintaSeIgnora() throws IOException {
        Path archivo = dir.resolve("meta-cache.json");
        CacheDisco cache = new CacheDisco(archivo);
        cache.guardar(snapshot());
        Files.writeString(archivo, Files.readString(archivo).replace("\"version\" : " + SnapshotMeta.VERSION, "\"version\" : 99"));

        assertThat(cache.cargar()).isEmpty();
    }

    @Test
    void creaElDirectorioPadreYNoDejaTemporales() throws IOException {
        Path archivo = dir.resolve("data").resolve("sub").resolve("meta-cache.json");
        CacheDisco cache = new CacheDisco(archivo);

        cache.guardar(snapshot());
        cache.guardar(snapshot());

        assertThat(archivo).exists();
        try (Stream<Path> archivos = Files.list(archivo.getParent())) {
            assertThat(archivos.map(p -> p.getFileName().toString()).toList()).containsExactly("meta-cache.json");
        }
    }

    @Test
    void borrarEliminaElArchivoYToleraQueNoExista() {
        Path archivo = dir.resolve("meta-cache.json");
        CacheDisco cache = new CacheDisco(archivo);
        cache.guardar(snapshot());

        cache.borrar();
        cache.borrar();

        assertThat(archivo).doesNotExist();
    }

    @Test
    void sinRutaConfiguradaNoHaceNada() {
        CacheDisco cache = new CacheDisco((Path) null);

        cache.guardar(snapshot());
        cache.borrar();

        assertThat(cache.cargar()).isEmpty();
        assertThat(cache.habilitada()).isFalse();
    }

    @Test
    void siNoSePuedeEscribirSoloAvisaYNoLanza() throws IOException {
        // El "directorio padre" es en realidad un archivo: crear el directorio falla.
        Path archivoComoPadre = dir.resolve("archivo");
        Files.writeString(archivoComoPadre, "x");
        CacheDisco cache = new CacheDisco(archivoComoPadre.resolve("meta-cache.json"));

        cache.guardar(snapshot());

        assertThat(cache.cargar()).isEmpty();
        assertThat(List.of(dir.toFile().list())).containsExactly("archivo");
    }
}
