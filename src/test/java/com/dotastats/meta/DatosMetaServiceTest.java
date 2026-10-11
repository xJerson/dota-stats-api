package com.dotastats.meta;

import com.dotastats.client.OpenDotaClient;
import com.dotastats.client.Parche;
import com.dotastats.exception.DatosNoCargadosException;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.juego.ArchivosJuegoClient;
import com.dotastats.juego.IndicadoresHabilidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatosMetaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-10T12:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    @TempDir
    Path dir;

    private OpenDotaClient client;
    private Path archivoCache;

    @BeforeEach
    void preparar() {
        archivoCache = dir.resolve("meta-cache.json");
        client = mock(OpenDotaClient.class);
        when(client.obtenerParche()).thenReturn(new Parche(60, "7.41"));
        when(client.obtenerHeroStats()).thenReturn(List.of(
                stat(1, "Alfa", List.of("Carry", "Nuker"), 6, 1000, 520, 7, 500, 260),
                stat(2, "Beta", List.of("Support"), 6, 800, 400, 7, 200, 100),
                stat(3, "Gamma", List.of("Carry"), 6, 300, 150, 7, 100, 50)));
        // Matchups desde la perspectiva del héroe consultado: las victorias son suyas.
        when(client.obtenerMatchups(2)).thenReturn(List.of(duelo(1, 90, 40), duelo(3, 50, 30)));
        when(client.obtenerMatchups(3)).thenReturn(List.of(duelo(1, 80, 20)));
    }

    private static MetaProperties props(List<Integer> brackets, long ttlParcheMin, long throttleMs, boolean alIniciar) {
        return new MetaProperties(brackets, throttleMs, alIniciar, ttlParcheMin, "ignorado.json");
    }

    private DatosMetaService servicio(Executor ejecutor) {
        return servicio(ejecutor, 0, 0, false);
    }

    private DatosMetaService servicio(Executor ejecutor, long ttlParcheMin, long throttleMs, boolean alIniciar) {
        return new DatosMetaService(client, props(List.of(6, 7), ttlParcheMin, throttleMs, alIniciar),
                new CacheDisco(archivoCache), ejecutor, RELOJ);
    }

    private static Map<String, Object> stat(int id, String nombre, List<String> roles, Object... bracketsYValores) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("localized_name", nombre);
        m.put("name", "npc_dota_hero_" + nombre.toLowerCase(java.util.Locale.ROOT));
        m.put("roles", roles);
        for (int i = 0; i < bracketsYValores.length; i += 3) {
            m.put(bracketsYValores[i] + "_pick", bracketsYValores[i + 1]);
            m.put(bracketsYValores[i] + "_win", bracketsYValores[i + 2]);
        }
        return m;
    }

    private static Map<String, Object> duelo(int heroId, int partidas, int victorias) {
        return Map.of("hero_id", heroId, "games_played", partidas, "wins", victorias);
    }

    @Test
    void antesDeCargarNoHayDatosYElEstadoLoDice() {
        DatosMetaService s = servicio(r -> { });

        assertThatThrownBy(s::datosParaSugerir).isInstanceOf(DatosNoCargadosException.class);
        assertThatThrownBy(() -> s.matchupsContra(List.of(2))).isInstanceOf(DatosNoCargadosException.class);
        EstadoMeta e = s.estado();
        assertThat(e.cargado()).isFalse();
        assertThat(e.cargando()).isFalse();
        assertThat(e.cargadoEn()).isNull();
        assertThat(e.heroesConMatchups()).isEmpty();
    }

    @Test
    void refrescarCargaHeroesAgregandoSoloLosBracketsConfigurados() {
        DatosMetaService s = servicio(Runnable::run);

        assertThat(s.refrescar()).isTrue();

        DatosMeta d = s.datosParaSugerir();
        HeroeMeta alfa = d.heroes().get(1);
        assertThat(alfa.nombre()).isEqualTo("Alfa");
        assertThat(alfa.roles()).containsExactlyInAnyOrder("Carry", "Nuker");
        assertThat(alfa.partidas()).isEqualTo(1500);
        assertThat(alfa.victorias()).isEqualTo(780);
        assertThat(d.parche()).isEqualTo(new Parche(60, "7.41"));
        assertThat(d.cargadoEn()).isEqualTo(AHORA);
    }

    @Test
    void elHeroeGuardaSuNombreInternoDeHeroStats() {
        DatosMetaService s = servicio(Runnable::run);

        s.refrescar();

        assertThat(s.datosParaSugerir().heroes().get(1).nombreInterno()).isEqualTo("npc_dota_hero_alfa");
    }

    @Test
    void otroRangoDeBracketsCambiaLosTotales() {
        DatosMetaService s = new DatosMetaService(client, props(List.of(7), 0, 0, false),
                new CacheDisco(archivoCache), Runnable::run, RELOJ);

        s.refrescar();

        assertThat(s.datosParaSugerir().heroes().get(1).partidas()).isEqualTo(500);
    }

    @Test
    void laCargaBaseNoPideMatchupsDeNingunHeroe() {
        DatosMetaService s = servicio(Runnable::run);

        s.refrescar();

        verify(client, times(1)).obtenerHeroStats();
        verify(client, times(1)).obtenerParche();
        verify(client, never()).obtenerMatchups(anyInt());
    }

    @Test
    void pideLosMatchupsDelEnemigoSoloLaPrimeraVezYLosDerivaDelComplemento() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();

        MatchupsEnemigos primera = s.matchupsContra(List.of(2));
        MatchupsEnemigos segunda = s.matchupsContra(List.of(2));

        // Beta ganó 40 de 90 contra Alfa, así que Alfa ganó 50 de 90 contra Beta.
        assertThat(primera.duelo(1, 2)).isEqualTo(new Duelo(90, 50));
        assertThat(segunda.duelo(1, 2)).isEqualTo(new Duelo(90, 50));
        assertThat(primera.duelo(3, 2)).isEqualTo(new Duelo(50, 20));
        assertThat(primera.duelo(2, 3)).isNull();
        assertThat(primera.fallidos()).isEmpty();
        verify(client, times(1)).obtenerMatchups(2);
    }

    @Test
    void conVariosEnemigosSoloPideLosQueFaltan() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        s.matchupsContra(List.of(2));

        MatchupsEnemigos r = s.matchupsContra(List.of(2, 3));

        assertThat(r.duelo(1, 3)).isEqualTo(new Duelo(80, 60));
        verify(client, times(1)).obtenerMatchups(2);
        verify(client, times(1)).obtenerMatchups(3);
        assertThat(s.estado().heroesConMatchups()).containsExactly(2, 3);
        assertThat(s.estado().matchupsEnCache()).isEqualTo(2);
    }

    @Test
    void peticionesConcurrentesPorElMismoHeroeHacenUnaSolaLlamada() throws Exception {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        AtomicInteger llamadas = new AtomicInteger();
        when(client.obtenerMatchups(2)).thenAnswer(inv -> {
            llamadas.incrementAndGet();
            Thread.sleep(200);
            return List.of(duelo(1, 90, 40));
        });
        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<MatchupsEnemigos>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    return s.matchupsContra(List.of(2));
                }));
            }
            salida.countDown();
            for (Future<MatchupsEnemigos> f : futuros) {
                MatchupsEnemigos r = f.get(10, TimeUnit.SECONDS);
                assertThat(r.duelo(1, 2)).isEqualTo(new Duelo(90, 50));
                assertThat(r.fallidos()).isEmpty();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(llamadas.get()).isEqualTo(1);
    }

    @Test
    void unFalloDeMatchupSeReportaYNoSeCacheaAsiQueSeReintenta() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        when(client.obtenerMatchups(2)).thenThrow(new OpenDotaNoDisponibleException("caído"));

        MatchupsEnemigos fallo = s.matchupsContra(List.of(2, 3));

        assertThat(fallo.fallidos()).containsExactly(2);
        assertThat(fallo.duelo(1, 3)).isEqualTo(new Duelo(80, 60));
        assertThat(s.estado().heroesConMatchups()).containsExactly(3);

        doReturn(List.of(duelo(1, 90, 40))).when(client).obtenerMatchups(2);
        MatchupsEnemigos exito = s.matchupsContra(List.of(2));

        assertThat(exito.fallidos()).isEmpty();
        assertThat(exito.duelo(1, 2)).isEqualTo(new Duelo(90, 50));
    }

    @Test
    void respetaLaPausaConfiguradaEntreFetchesConsecutivos() {
        DatosMetaService s = servicio(Runnable::run, 0, 120, false);
        s.refrescar();

        long inicio = System.nanoTime();
        s.matchupsContra(List.of(2, 3));
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

        assertThat(ms).isGreaterThanOrEqualTo(100); // una pausa entre el primer fetch y el segundo
        assertThat(ms).isLessThan(2000);
    }

    @Test
    void sinPausaConfiguradaNoEspera() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();

        long inicio = System.nanoTime();
        s.matchupsContra(List.of(2, 3));

        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio)).isLessThan(100);
    }

    // --- Descarga paralela de matchups ---

    @Test
    void conVariosEnemigosFaltantesLosPideEnParalelo() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        // Las tres llamadas deben estar activas a la vez para que se abra la puerta; en serie nunca ocurre.
        CountDownLatch puerta = new CountDownLatch(3);
        AtomicInteger sinCoincidir = new AtomicInteger();
        for (int id : new int[]{2, 3, 4}) {
            when(client.obtenerMatchups(id)).thenAnswer(inv -> {
                puerta.countDown();
                if (!puerta.await(5, TimeUnit.SECONDS)) {
                    sinCoincidir.incrementAndGet();
                }
                return List.of(duelo(1, 100, 40));
            });
        }

        MatchupsEnemigos r = s.matchupsContra(List.of(2, 3, 4));

        assertThat(sinCoincidir.get()).isZero();
        assertThat(r.fallidos()).isEmpty();
        assertThat(r.porEnemigo()).containsOnlyKeys(2, 3, 4);
        assertThat(s.estado().heroesConMatchups()).containsExactly(2, 3, 4);
    }

    @Test
    void laDescargaParalelaNuncaSuperaCincoPeticionesSimultaneas() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        AtomicInteger activas = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        when(client.obtenerMatchups(anyInt())).thenAnswer(inv -> {
            maximo.accumulateAndGet(activas.incrementAndGet(), Math::max);
            Thread.sleep(150);
            activas.decrementAndGet();
            return List.of(duelo(1, 100, 40));
        });

        MatchupsEnemigos r = s.matchupsContra(List.of(10, 11, 12, 13, 14, 15, 16, 17));

        assertThat(r.porEnemigo()).hasSize(8);
        assertThat(maximo.get()).isBetween(2, 5);
    }

    @Test
    void conPausaConfiguradaLaDescargaSigueSiendoEnSerie() {
        DatosMetaService s = servicio(Runnable::run, 0, 20, false);
        s.refrescar();
        AtomicInteger activas = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        when(client.obtenerMatchups(anyInt())).thenAnswer(inv -> {
            maximo.accumulateAndGet(activas.incrementAndGet(), Math::max);
            Thread.sleep(50);
            activas.decrementAndGet();
            return List.of(duelo(1, 100, 40));
        });

        s.matchupsContra(List.of(2, 3, 4));

        assertThat(maximo.get()).isEqualTo(1);
    }

    @Test
    void unFalloEnLaDescargaParalelaNoSeCacheaYNoImpideLosDemas() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        when(client.obtenerMatchups(3)).thenThrow(new OpenDotaNoDisponibleException("caído"));
        when(client.obtenerMatchups(4)).thenReturn(List.of(duelo(1, 100, 40)));

        MatchupsEnemigos r = s.matchupsContra(List.of(2, 3, 4));

        assertThat(r.fallidos()).containsExactly(3);
        assertThat(r.porEnemigo()).containsOnlyKeys(2, 4);
        assertThat(s.estado().heroesConMatchups()).containsExactly(2, 4);

        doReturn(List.of(duelo(1, 100, 40))).when(client).obtenerMatchups(3);
        assertThat(s.matchupsContra(List.of(3)).fallidos()).isEmpty();
        verify(client, times(1)).obtenerMatchups(2);
        verify(client, times(1)).obtenerMatchups(4);
    }

    @Test
    void tambienSeCompartePeticionEntreLlamadasConcurrentesQueSolapanEnemigos() throws Exception {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        AtomicInteger llamadas = new AtomicInteger();
        when(client.obtenerMatchups(anyInt())).thenAnswer(inv -> {
            llamadas.incrementAndGet();
            Thread.sleep(150);
            return List.of(duelo(1, 100, 40));
        });
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<MatchupsEnemigos>> futuros = new ArrayList<>();
            List<List<Integer>> pedidos = List.of(List.of(2, 3), List.of(3, 4), List.of(2, 4), List.of(2, 3, 4));
            for (List<Integer> enemigos : pedidos) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    return s.matchupsContra(enemigos);
                }));
            }
            salida.countDown();
            for (Future<MatchupsEnemigos> f : futuros) {
                assertThat(f.get(10, TimeUnit.SECONDS).fallidos()).isEmpty();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(llamadas.get()).isEqualTo(3);
    }

    @Test
    void escribeEnDiscoUnaSolaVezTrasTodoElLoteYNadaSiTodoEstabaEnCache() {
        CacheDisco cacheEspiado = spy(new CacheDisco(archivoCache));
        DatosMetaService s = new DatosMetaService(client, props(List.of(6, 7), 0, 0, false),
                cacheEspiado, Runnable::run, RELOJ);
        s.refrescar();
        clearInvocations(cacheEspiado);
        when(client.obtenerMatchups(4)).thenReturn(List.of(duelo(1, 100, 40)));

        s.matchupsContra(List.of(2, 3, 4));
        verify(cacheEspiado, times(1)).guardar(any());

        s.matchupsContra(List.of(2, 3, 4));
        verify(cacheEspiado, times(1)).guardar(any());
        assertThat(new CacheDisco(archivoCache).cargar().orElseThrow().matchups()).containsOnlyKeys(2, 3, 4);
    }

    @Test
    void siTodosLosFetchesFallanNoEscribeEnDisco() {
        CacheDisco cacheEspiado = spy(new CacheDisco(archivoCache));
        DatosMetaService s = new DatosMetaService(client, props(List.of(6, 7), 0, 0, false),
                cacheEspiado, Runnable::run, RELOJ);
        s.refrescar();
        clearInvocations(cacheEspiado);
        when(client.obtenerMatchups(anyInt())).thenThrow(new OpenDotaNoDisponibleException("caído"));

        MatchupsEnemigos r = s.matchupsContra(List.of(2, 3));

        assertThat(r.fallidos()).containsExactlyInAnyOrder(2, 3);
        verify(cacheEspiado, never()).guardar(any());
    }

    @Test
    void estadoDespuesDeCargarReportaParcheYCache() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        s.matchupsContra(List.of(3));

        EstadoMeta e = s.estado();

        assertThat(e.cargado()).isTrue();
        assertThat(e.cargando()).isFalse();
        assertThat(e.parche()).isEqualTo("7.41");
        assertThat(e.cargadoEn()).isEqualTo(AHORA);
        assertThat(e.obsoleto()).isFalse();
        assertThat(e.matchupsEnCache()).isEqualTo(1);
        assertThat(e.heroesConMatchups()).containsExactly(3);
        assertThat(e.cacheDiscoUsada()).isFalse();
        assertThat(e.ultimoError()).isNull();
    }

    @Test
    void noArrancaUnaSegundaCargaMientrasHayUnaEnCurso() {
        List<Runnable> pendientes = new ArrayList<>();
        DatosMetaService s = servicio(pendientes::add);

        assertThat(s.refrescar()).isTrue();
        assertThat(s.estado().cargando()).isTrue();
        assertThat(s.refrescar()).isFalse();
        assertThat(pendientes).hasSize(1);

        pendientes.get(0).run();

        assertThat(s.estado().cargando()).isFalse();
        assertThat(s.refrescar()).isTrue();
        assertThat(pendientes).hasSize(2);
    }

    @Test
    void siFallaLaRecargaConservaLosDatosAnterioresYGuardaElError() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        DatosMeta anterior = s.datosParaSugerir();
        when(client.obtenerHeroStats()).thenThrow(new OpenDotaNoDisponibleException("límite"));

        s.refrescar();

        assertThat(s.datosParaSugerir()).isSameAs(anterior);
        EstadoMeta e = s.estado();
        assertThat(e.cargando()).isFalse();
        assertThat(e.ultimoError()).contains("límite");
    }

    @Test
    void siFallaLaPrimeraCargaSigueSinDatos() {
        when(client.obtenerHeroStats()).thenThrow(new OpenDotaNoDisponibleException("caído"));
        DatosMetaService s = servicio(Runnable::run);

        s.refrescar();

        assertThatThrownBy(s::datosParaSugerir).isInstanceOf(DatosNoCargadosException.class);
        assertThat(s.estado().cargando()).isFalse();
        assertThat(s.estado().ultimoError()).contains("caído");
    }

    @Test
    void marcaObsoletoCuandoElParcheVivoEsDistintoAlCargado() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        when(client.obtenerParche()).thenReturn(new Parche(61, "7.42"));

        EstadoMeta e = s.estado();

        assertThat(e.obsoleto()).isTrue();
        assertThat(e.parche()).isEqualTo("7.41");
        assertThat(e.parcheActual()).isEqualTo("7.42");
    }

    @Test
    void siNoSePuedeConsultarElParcheVivoNoSeMarcaObsoleto() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        when(client.obtenerParche()).thenThrow(new OpenDotaNoDisponibleException("x"));

        assertThat(s.estado().obsoleto()).isFalse();
    }

    @Test
    void elParcheVivoSeCacheaDentroDelTtl() {
        DatosMetaService s = servicio(Runnable::run, 30, 0, false);
        s.refrescar(); // consulta el parche una vez

        s.estado();
        s.estado();

        verify(client, times(1)).obtenerParche();
    }

    @Test
    void estadoSinDatosNoConsultaMatchupsNiParche() {
        DatosMetaService s = servicio(r -> { });

        s.estado();

        verify(client, never()).obtenerParche();
        verify(client, never()).obtenerMatchups(anyInt());
    }

    @Test
    void conParcheNuevoElSiguienteSugerirDescartaMatchupsYHeroStatsYRecargaLosHeroStats() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        s.matchupsContra(List.of(2));
        when(client.obtenerParche()).thenReturn(new Parche(61, "7.42"));
        assertThat(s.estado().obsoleto()).isTrue();

        DatosMeta nuevos = s.datosParaSugerir();

        assertThat(nuevos.parche()).isEqualTo(new Parche(61, "7.42"));
        verify(client, times(2)).obtenerHeroStats();
        verify(client, never()).obtenerMatchups(3);
        EstadoMeta e = s.estado();
        assertThat(e.obsoleto()).isFalse();
        assertThat(e.matchupsEnCache()).isZero();
        assertThat(e.parche()).isEqualTo("7.42");
        // Los matchups del parche viejo no sobreviven: se vuelven a pedir.
        s.matchupsContra(List.of(2));
        verify(client, times(2)).obtenerMatchups(2);
    }

    @Test
    void siLaRecargaPorParcheNuevoFallaSeSiguenUsandoLosDatosViejos() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        DatosMeta viejos = s.datosParaSugerir();
        when(client.obtenerParche()).thenReturn(new Parche(61, "7.42"));
        when(client.obtenerHeroStats()).thenThrow(new OpenDotaNoDisponibleException("caído"));

        assertThat(s.datosParaSugerir()).isSameAs(viejos);
        assertThat(s.estado().obsoleto()).isTrue();
        assertThat(s.estado().ultimoError()).contains("caído");
    }

    @Test
    void sinParcheNuevoSugerirNoRecargaNada() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();

        s.datosParaSugerir();
        s.datosParaSugerir();

        verify(client, times(1)).obtenerHeroStats();
    }

    @Test
    void refrescarVaciaLaCacheEnMemoriaYEnDiscoYRecargaLoBase() throws Exception {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        s.matchupsContra(List.of(2));
        assertThat(Files.readString(archivoCache)).contains("\"2\"");

        s.refrescar();

        assertThat(s.estado().matchupsEnCache()).isZero();
        verify(client, times(2)).obtenerHeroStats();
        SnapshotMeta enDisco = new CacheDisco(archivoCache).cargar().orElseThrow();
        assertThat(enDisco.matchups()).isEmpty();
        assertThat(enDisco.datos().heroes()).hasSize(3);
    }

    @Test
    void cadaMatchupNuevoSeGuardaEnDisco() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();

        s.matchupsContra(List.of(2, 3));

        SnapshotMeta enDisco = new CacheDisco(archivoCache).cargar().orElseThrow();
        assertThat(enDisco.matchups()).containsOnlyKeys(2, 3);
        assertThat(enDisco.matchups().get(2).get(1)).isEqualTo(new Duelo(90, 40));
    }

    @Test
    void alIniciarReutilizaLaCacheDeDiscoSinTocarLaRed() {
        DatosMetaService primero = servicio(Runnable::run);
        primero.refrescar();
        primero.matchupsContra(List.of(2));

        OpenDotaClient otroClient = mock(OpenDotaClient.class);
        DatosMetaService segundo = new DatosMetaService(otroClient, props(List.of(6, 7), 30, 0, true),
                new CacheDisco(archivoCache), Runnable::run, RELOJ);
        segundo.cargarAlIniciar();

        assertThat(segundo.datosParaSugerir().heroes()).hasSize(3);
        MatchupsEnemigos m = segundo.matchupsContra(List.of(2));
        assertThat(m.duelo(1, 2)).isEqualTo(new Duelo(90, 50));
        verify(otroClient, never()).obtenerHeroStats();
        verify(otroClient, never()).obtenerMatchups(anyInt());
        assertThat(segundo.estado().cacheDiscoUsada()).isTrue();
        assertThat(segundo.estado().heroesConMatchups()).containsExactly(2);
    }

    @Test
    void alIniciarConCacheCorruptaLaIgnoraYCargaDeLaRed() throws Exception {
        Files.writeString(archivoCache, "basura {{{");
        DatosMetaService s = servicio(Runnable::run, 0, 0, true);

        s.cargarAlIniciar();

        assertThat(s.datosParaSugerir().heroes()).hasSize(3);
        assertThat(s.estado().cacheDiscoUsada()).isFalse();
        verify(client, times(1)).obtenerHeroStats();
        // y la deja reparada
        assertThat(new CacheDisco(archivoCache).cargar()).isPresent();
    }

    @Test
    void alIniciarSinCacheCargaSoloHeroStatsYParche() {
        DatosMetaService s = servicio(Runnable::run, 0, 0, true);

        s.cargarAlIniciar();

        verify(client, times(1)).obtenerHeroStats();
        verify(client, times(1)).obtenerParche();
        verify(client, never()).obtenerMatchups(anyInt());
    }

    @Test
    void conCargarAlIniciarDesactivadoNoHaceNada() {
        DatosMetaService s = servicio(Runnable::run, 0, 0, false);

        s.cargarAlIniciar();

        verify(client, never()).obtenerHeroStats();
    }

    @Test
    void laCacheDeDiscoDeOtroParcheSeDescartaAlDetectarloEnElSiguienteSugerir() {
        DatosMetaService primero = servicio(Runnable::run);
        primero.refrescar();
        primero.matchupsContra(List.of(2));
        when(client.obtenerParche()).thenReturn(new Parche(61, "7.42"));

        DatosMetaService segundo = servicio(Runnable::run, 0, 0, true);
        segundo.cargarAlIniciar();
        DatosMeta d = segundo.datosParaSugerir();

        assertThat(d.parche()).isEqualTo(new Parche(61, "7.42"));
        assertThat(segundo.estado().matchupsEnCache()).isZero();
        assertThat(new CacheDisco(archivoCache).cargar().orElseThrow().matchups()).isEmpty();
    }

    private static Map<String, Object> fila(int heroId, int lane, int tiempo, String partidas, String victorias) {
        return Map.of("hero_id", heroId, "lane_role", lane, "time", tiempo, "games", partidas, "wins", victorias);
    }

    private void lineasDisponibles() {
        when(client.obtenerLaneRoles(1)).thenReturn(List.of(
                fila(1, 1, 900, "5", "2"), fila(1, 1, 1800, "20", "11"), fila(2, 1, 900, "7", "3")));
        when(client.obtenerLaneRoles(2)).thenReturn(List.of(fila(1, 2, 900, "3", "1")));
        when(client.obtenerLaneRoles(3)).thenReturn(List.of(fila(3, 3, 900, "40", "22")));
    }

    @Test
    void cargaLasTresLineasSumandoLosTramosDeTiempoYConvirtiendoLasCadenas() {
        lineasDisponibles();
        DatosMetaService s = servicio(Runnable::run);

        s.refrescar();

        DatosMeta d = s.datosParaSugerir();
        Map<Integer, Map<Integer, Long>> partidas = d.partidasPorLinea();
        assertThat(partidas.get(1)).containsOnly(Map.entry(1, 25L), Map.entry(2, 7L));
        assertThat(partidas.get(2)).containsOnly(Map.entry(1, 3L));
        assertThat(partidas.get(3)).containsOnly(Map.entry(3, 40L));
        Map<Integer, Map<Integer, Long>> victorias = d.victoriasPorLinea();
        assertThat(victorias.get(1)).containsOnly(Map.entry(1, 13L), Map.entry(2, 3L));
        assertThat(victorias.get(2)).containsOnly(Map.entry(1, 1L));
        assertThat(victorias.get(3)).containsOnly(Map.entry(3, 22L));
        verify(client, never()).obtenerLaneRoles(4);
    }

    @Test
    void siFallaUnaLineaNoHayDatosDeLineaPeroElMetaSeCargaIgual() {
        lineasDisponibles();
        when(client.obtenerLaneRoles(2)).thenThrow(new OpenDotaNoDisponibleException("límite"));
        DatosMetaService s = servicio(Runnable::run);

        s.refrescar();

        DatosMeta d = s.datosParaSugerir();
        assertThat(d.heroes()).hasSize(3);
        assertThat(d.partidasPorLinea()).isEmpty();
        assertThat(d.victoriasPorLinea()).isEmpty();
    }

    @Test
    void unaLineaVaciaTambienSeConsideraNoDisponible() {
        lineasDisponibles();
        when(client.obtenerLaneRoles(3)).thenReturn(List.of());
        DatosMetaService s = servicio(Runnable::run);

        s.refrescar();

        assertThat(s.datosParaSugerir().partidasPorLinea()).isEmpty();
        assertThat(s.datosParaSugerir().victoriasPorLinea()).isEmpty();
    }

    @Test
    void lasLineasSePersistenEnDiscoYAlIniciarSeReutilizanSinTocarLaRed() {
        lineasDisponibles();
        servicio(Runnable::run).refrescar();

        OpenDotaClient otroClient = mock(OpenDotaClient.class);
        DatosMetaService segundo = new DatosMetaService(otroClient, props(List.of(6, 7), 30, 0, true),
                new CacheDisco(archivoCache), Runnable::run, RELOJ);
        segundo.cargarAlIniciar();

        assertThat(segundo.datosParaSugerir().partidasPorLinea().get(1)).containsEntry(1, 25L);
        assertThat(segundo.datosParaSugerir().victoriasPorLinea().get(1)).containsEntry(1, 13L);
        verify(otroClient, never()).obtenerLaneRoles(anyInt());
    }

    // --- /constants/abilities y /constants/hero_abilities (CatalogoHabilidades) ---

    private void habilidadesDisponibles() {
        // doReturn (no when): alguna llamada ya pudo dejar el mock devolviendo una excepción, y when(mock.metodo())
        // invoca el método al construir el stub, lo que relanzaría esa excepción antes de poder reemplazarlo.
        doReturn(Map.of("lion_voodoo", Map.of("dname", "Hex"))).when(client).obtenerAbilidades();
        doReturn(Map.of("npc_dota_hero_alfa", Map.of("abilities", List.of("lion_voodoo"))))
                .when(client).obtenerHabilidadesPorHeroe();
    }

    @Test
    void datosHabilidadAntesDeCargarLanzaDatosNoCargados() {
        DatosMetaService s = servicio(r -> { });

        assertThatThrownBy(s::datosHabilidad).isInstanceOf(DatosNoCargadosException.class);
    }

    @Test
    void laCargaBaseNoPideAbilidadesNiHabilidadesPorHeroe() {
        DatosMetaService s = servicio(Runnable::run);

        s.refrescar();

        verify(client, never()).obtenerAbilidades();
        verify(client, never()).obtenerHabilidadesPorHeroe();
    }

    @Test
    void datosHabilidadSePidenUnaSolaVezYSeCacheanEnMemoria() {
        habilidadesDisponibles();
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();

        DatosHabilidad primera = s.datosHabilidad();
        DatosHabilidad segunda = s.datosHabilidad();

        assertThat(primera.abilidades()).containsKey("lion_voodoo");
        assertThat(primera.habilidadesPorHeroe()).containsKey("npc_dota_hero_alfa");
        assertThat(segunda).isEqualTo(primera);
        verify(client, times(1)).obtenerAbilidades();
        verify(client, times(1)).obtenerHabilidadesPorHeroe();
    }

    @Test
    void siFallaObtenerAbilidadesNoSeCacheaYSeReintentaEnLaSiguienteLlamada() {
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        when(client.obtenerAbilidades()).thenThrow(new OpenDotaNoDisponibleException("caído"));

        assertThatThrownBy(s::datosHabilidad).isInstanceOf(OpenDotaNoDisponibleException.class);

        habilidadesDisponibles();
        DatosHabilidad ok = s.datosHabilidad();
        assertThat(ok.abilidades()).isNotEmpty();
    }

    @Test
    void datosHabilidadSePersistenEnDiscoYAlIniciarSeReutilizanSinTocarLaRed() {
        habilidadesDisponibles();
        DatosMetaService primero = servicio(Runnable::run);
        primero.refrescar();
        primero.datosHabilidad();

        OpenDotaClient otroClient = mock(OpenDotaClient.class);
        DatosMetaService segundo = new DatosMetaService(otroClient, props(List.of(6, 7), 30, 0, true),
                new CacheDisco(archivoCache), Runnable::run, RELOJ);
        segundo.cargarAlIniciar();

        DatosHabilidad reutilizado = segundo.datosHabilidad();
        assertThat(reutilizado.abilidades()).containsKey("lion_voodoo");
        verify(otroClient, never()).obtenerAbilidades();
        verify(otroClient, never()).obtenerHabilidadesPorHeroe();
    }

    @Test
    void conParcheNuevoDatosHabilidadSeDescartaYSeVuelveAPedir() {
        habilidadesDisponibles();
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        s.datosHabilidad();
        when(client.obtenerParche()).thenReturn(new Parche(61, "7.42"));

        s.datosParaSugerir(); // detecta el parche nuevo y recarga la base
        s.datosHabilidad();

        verify(client, times(2)).obtenerAbilidades();
        verify(client, times(2)).obtenerHabilidadesPorHeroe();
    }

    // --- /constants/heroes (alcance de ataque para la herramienta RANGO_MAYOR) ---

    private void heroesConstantesDisponibles(OpenDotaClient cliente) {
        doReturn(Map.of(
                "17", Map.of("id", 17, "localized_name", "Storm Spirit", "attack_range", 480),
                "47", Map.of("id", 47, "localized_name", "Viper", "attack_range", 575)))
                .when(cliente).obtenerHeroesConstantes();
    }

    @Test
    void datosHabilidadIncluyeElAlcanceDeAtaqueDeCadaHeroeYLoPideUnaSolaVez() {
        habilidadesDisponibles();
        heroesConstantesDisponibles(client);
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();

        DatosHabilidad primera = s.datosHabilidad();
        DatosHabilidad segunda = s.datosHabilidad();

        assertThat(primera.rangoAtaquePorHeroe()).containsEntry(17, 480).containsEntry(47, 575);
        assertThat(segunda).isEqualTo(primera);
        verify(client, times(1)).obtenerHeroesConstantes();
    }

    @Test
    void siFallaObtenerLosHeroesConstantesLaFilaSigueSinAlcanceYNoSeCachea() {
        habilidadesDisponibles();
        doThrow(new OpenDotaNoDisponibleException("caído")).when(client).obtenerHeroesConstantes();
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();

        DatosHabilidad sin = s.datosHabilidad();

        assertThat(sin.abilidades()).containsKey("lion_voodoo");
        assertThat(sin.rangoAtaquePorHeroe()).isEmpty();

        heroesConstantesDisponibles(client);
        assertThat(s.datosHabilidad().rangoAtaquePorHeroe()).containsEntry(47, 575);
    }

    @Test
    void elAlcanceSePersisteEnDiscoYAlIniciarSeReutilizaSinTocarLaRed() {
        habilidadesDisponibles();
        heroesConstantesDisponibles(client);
        DatosMetaService primero = servicio(Runnable::run);
        primero.refrescar();
        primero.datosHabilidad();

        OpenDotaClient otroClient = mock(OpenDotaClient.class);
        DatosMetaService segundo = new DatosMetaService(otroClient, props(List.of(6, 7), 30, 0, true),
                new CacheDisco(archivoCache), Runnable::run, RELOJ);
        segundo.cargarAlIniciar();

        assertThat(segundo.datosHabilidad().rangoAtaquePorHeroe()).containsEntry(47, 575);
        verify(otroClient, never()).obtenerHeroesConstantes();
    }

    @Test
    void unaCacheViejaSinAlcancePideSoloLosHeroesConstantesYNoRepiteLasHabilidades() {
        habilidadesDisponibles();
        DatosMetaService primero = servicio(Runnable::run);
        primero.refrescar();
        primero.datosHabilidad(); // el mock devuelve {}: queda en disco sin alcance

        OpenDotaClient otroClient = mock(OpenDotaClient.class);
        heroesConstantesDisponibles(otroClient);
        DatosMetaService segundo = new DatosMetaService(otroClient, props(List.of(6, 7), 30, 0, true),
                new CacheDisco(archivoCache), Runnable::run, RELOJ);
        segundo.cargarAlIniciar();

        DatosHabilidad datos = segundo.datosHabilidad();

        assertThat(datos.abilidades()).containsKey("lion_voodoo");
        assertThat(datos.rangoAtaquePorHeroe()).containsEntry(47, 575);
        verify(otroClient, never()).obtenerAbilidades();
        verify(otroClient, never()).obtenerHabilidadesPorHeroe();
        verify(otroClient, times(1)).obtenerHeroesConstantes();
    }

    @Test
    void conParcheNuevoElAlcanceTambienSeDescartaYSeVuelveAPedir() {
        habilidadesDisponibles();
        heroesConstantesDisponibles(client);
        DatosMetaService s = servicio(Runnable::run);
        s.refrescar();
        s.datosHabilidad();
        when(client.obtenerParche()).thenReturn(new Parche(61, "7.42"));

        s.datosParaSugerir();
        s.datosHabilidad();

        verify(client, times(2)).obtenerHeroesConstantes();
    }

    // --- Archivos del juego (indicadores de habilidad que OpenDota no trae) ---

    private static final IndicadoresHabilidad BLINK = new IndicadoresHabilidad(true, false, false, false, false, false, false);
    private static final IndicadoresHabilidad OTRO = new IndicadoresHabilidad(false, true, false, false, false, false, false);
    private static final List<String> TODOS = List.of("npc_dota_hero_alfa", "npc_dota_hero_beta", "npc_dota_hero_gamma");

    private static Map<String, Map<String, IndicadoresHabilidad>> indicadores(String... heroes) {
        Map<String, Map<String, IndicadoresHabilidad>> r = new HashMap<>();
        for (String h : heroes) {
            r.put(h, Map.of(h + "_habilidad", BLINK));
        }
        return r;
    }

    private static Map<String, Map<String, IndicadoresHabilidad>> indicadoresDeTodos() {
        return indicadores(TODOS.toArray(new String[0]));
    }

    /** Servicio con los dos ejecutores en línea: la carga base y el calentamiento corren al llamar a refrescar(). */
    private DatosMetaService servicioConJuego(OpenDotaClient cliente, ArchivosJuegoClient juego, boolean alIniciar) {
        return new DatosMetaService(cliente, props(List.of(6, 7), 0, 0, alIniciar), new CacheDisco(archivoCache),
                Runnable::run, RELOJ, juego);
    }

    /** Servicio cuyo calentamiento NO corre solo (ejecutor que descarta): el test lo dispara a mano. */
    private DatosMetaService servicioSinCalentar(ArchivosJuegoClient juego) {
        return new DatosMetaService(client, props(List.of(6, 7), 0, 0, false), new CacheDisco(archivoCache),
                Runnable::run, RELOJ, juego, r -> { });
    }

    @Test
    void datosHabilidadNuncaPideLosArchivosDelJuego() {
        habilidadesDisponibles();
        ArchivosJuegoClient juego = mock(ArchivosJuegoClient.class);
        DatosMetaService s = servicioSinCalentar(juego);
        s.refrescar();

        for (int i = 0; i < 3; i++) {
            assertThat(s.datosHabilidad().indicadoresPorHeroe()).isEmpty();
        }

        verify(juego, never()).obtenerIndicadores(any());
    }

    @Test
    void alConstruirElSnapshotCalientaLosIndicadoresDeTodosLosHeroesYLosEntregaDatosHabilidad() {
        habilidadesDisponibles();
        ArchivosJuegoClient juego = mock(ArchivosJuegoClient.class);
        when(juego.obtenerIndicadores(any())).thenReturn(indicadoresDeTodos());
        DatosMetaService s = servicioConJuego(client, juego, false);
        long antes = s.versionIndicadoresJuego();

        s.refrescar();

        DatosHabilidad dh = s.datosHabilidad();
        assertThat(dh.indicadoresPorHeroe()).containsOnlyKeys(TODOS.toArray(new String[0]));
        assertThat(s.versionIndicadoresJuego()).isGreaterThan(antes);
        verify(juego, times(1)).obtenerIndicadores(org.mockito.ArgumentMatchers.argThat(n -> List.copyOf(n).equals(TODOS)));
    }

    @Test
    void unResultadoVacioNoSeGuardaNiSeDaPorFinalYElSiguienteCalentamientoLoReintenta() {
        habilidadesDisponibles();
        ArchivosJuegoClient juego = mock(ArchivosJuegoClient.class);
        when(juego.obtenerIndicadores(any())).thenReturn(Map.of());
        DatosMetaService s = servicioConJuego(client, juego, false);
        s.refrescar();
        long versionSinDatos = s.versionIndicadoresJuego();

        assertThat(s.datosHabilidad().indicadoresPorHeroe()).isEmpty();
        assertThat(new CacheDisco(archivoCache).cargar().orElseThrow().indicadoresPorHeroe()).isEmpty();

        when(juego.obtenerIndicadores(any())).thenReturn(indicadoresDeTodos());
        s.calentarIndicadores();

        assertThat(s.datosHabilidad().indicadoresPorHeroe()).hasSize(3);
        assertThat(s.versionIndicadoresJuego()).isGreaterThan(versionSinDatos);
    }

    @Test
    void unaExcepcionDelClienteDelJuegoNoTumbaNadaYSeReintentaDespues() {
        habilidadesDisponibles();
        ArchivosJuegoClient juego = mock(ArchivosJuegoClient.class);
        when(juego.obtenerIndicadores(any())).thenThrow(new IllegalStateException("explotó"));
        DatosMetaService s = servicioConJuego(client, juego, false);

        s.refrescar();

        assertThat(s.datosHabilidad().abilidades()).containsKey("lion_voodoo");
        assertThat(s.datosHabilidad().indicadoresPorHeroe()).isEmpty();
        doReturn(indicadoresDeTodos()).when(juego).obtenerIndicadores(any());
        s.calentarIndicadores();
        assertThat(s.datosHabilidad().indicadoresPorHeroe()).hasSize(3);
    }

    @Test
    void unResultadoParcialSeUsaPeroNoSeDaPorCompletoYElSiguienteCalentamientoPideSoloLosQueFaltan() {
        habilidadesDisponibles();
        ArchivosJuegoClient juego = mock(ArchivosJuegoClient.class);
        when(juego.obtenerIndicadores(any())).thenReturn(indicadores("npc_dota_hero_alfa"));
        DatosMetaService primero = servicioConJuego(client, juego, false);
        primero.refrescar();
        assertThat(primero.datosHabilidad().indicadoresPorHeroe()).containsOnlyKeys("npc_dota_hero_alfa");

        // Al reiniciar: la caché de disco trae solo a alfa; el calentamiento pide únicamente a beta y gamma.
        OpenDotaClient otroClient = mock(OpenDotaClient.class);
        ArchivosJuegoClient otroJuego = mock(ArchivosJuegoClient.class);
        when(otroJuego.obtenerIndicadores(any())).thenReturn(indicadores("npc_dota_hero_beta", "npc_dota_hero_gamma"));
        DatosMetaService segundo = new DatosMetaService(otroClient, props(List.of(6, 7), 30, 0, true),
                new CacheDisco(archivoCache), Runnable::run, RELOJ, otroJuego);
        segundo.cargarAlIniciar();

        verify(otroJuego, times(1)).obtenerIndicadores(org.mockito.ArgumentMatchers.argThat(
                n -> List.copyOf(n).equals(List.of("npc_dota_hero_beta", "npc_dota_hero_gamma"))));
        assertThat(segundo.datosHabilidad().indicadoresPorHeroe()).containsOnlyKeys(TODOS.toArray(new String[0]));
    }

    @Test
    void conUnaCacheCompletaNoSePideNadaAlJuego() {
        habilidadesDisponibles();
        ArchivosJuegoClient juego = mock(ArchivosJuegoClient.class);
        when(juego.obtenerIndicadores(any())).thenReturn(indicadoresDeTodos());
        DatosMetaService primero = servicioConJuego(client, juego, false);
        primero.refrescar();
        primero.datosHabilidad();

        OpenDotaClient otroClient = mock(OpenDotaClient.class);
        ArchivosJuegoClient otroJuego = mock(ArchivosJuegoClient.class);
        DatosMetaService segundo = new DatosMetaService(otroClient, props(List.of(6, 7), 30, 0, true),
                new CacheDisco(archivoCache), Runnable::run, RELOJ, otroJuego);
        segundo.cargarAlIniciar();

        assertThat(segundo.datosHabilidad().indicadoresPorHeroe()).hasSize(3);
        verify(otroJuego, never()).obtenerIndicadores(any());
        verify(otroClient, never()).obtenerAbilidades();
    }

    @Test
    void unaCacheViejaSinIndicadoresPideSoloLosIndicadoresYNoRepiteLoDeOpenDota() {
        habilidadesDisponibles();
        heroesConstantesDisponibles(client);
        DatosMetaService primero = servicioConJuego(client, ArchivosJuegoClient.NINGUNO, false);
        primero.refrescar();
        primero.datosHabilidad(); // sin archivos del juego: queda en disco con habilidades y alcance, sin indicadores

        OpenDotaClient otroClient = mock(OpenDotaClient.class);
        ArchivosJuegoClient juego = mock(ArchivosJuegoClient.class);
        when(juego.obtenerIndicadores(any())).thenReturn(indicadoresDeTodos());
        DatosMetaService segundo = new DatosMetaService(otroClient, props(List.of(6, 7), 30, 0, true),
                new CacheDisco(archivoCache), Runnable::run, RELOJ, juego);
        segundo.cargarAlIniciar();

        DatosHabilidad datos = segundo.datosHabilidad();

        assertThat(datos.abilidades()).containsKey("lion_voodoo");
        assertThat(datos.rangoAtaquePorHeroe()).containsEntry(47, 575);
        assertThat(datos.indicadoresPorHeroe()).hasSize(3);
        verify(otroClient, never()).obtenerAbilidades();
        verify(otroClient, never()).obtenerHabilidadesPorHeroe();
        verify(otroClient, never()).obtenerHeroesConstantes();
        verify(juego, times(1)).obtenerIndicadores(any());
    }

    @Test
    void conParcheNuevoLosIndicadoresSeDescartanYSeVuelvenAPedirAlReconstruirElSnapshot() {
        habilidadesDisponibles();
        ArchivosJuegoClient juego = mock(ArchivosJuegoClient.class);
        when(juego.obtenerIndicadores(any())).thenReturn(indicadoresDeTodos());
        DatosMetaService s = servicioConJuego(client, juego, false); // TTL 0: consulta el parche vivo siempre
        s.refrescar();
        when(client.obtenerParche()).thenReturn(new Parche(61, "7.42"));

        s.datosParaSugerir(); // detecta el parche nuevo, recarga la base y calienta de nuevo

        verify(juego, times(2)).obtenerIndicadores(any());
    }

    @Test
    void siElSnapshotCambiaMientrasSeDescargaElResultadoViejoSeDescarta() {
        habilidadesDisponibles();
        ArchivosJuegoClient juego = mock(ArchivosJuegoClient.class);
        DatosMetaService s = servicioSinCalentar(juego);
        s.refrescar();
        AtomicInteger llamadas = new AtomicInteger();
        when(juego.obtenerIndicadores(any())).thenAnswer(inv -> {
            if (llamadas.incrementAndGet() == 1) {
                s.refrescar(); // mientras se descarga llega un parche nuevo: el snapshot se reemplaza
                return indicadores("npc_dota_hero_alfa"); // resultado de la generación vieja
            }
            return Map.of("npc_dota_hero_beta", Map.of("beta_habilidad", OTRO)); // generación nueva
        });

        s.calentarIndicadores();

        // Lo de la generación vieja (alfa) se descartó; solo queda lo de la nueva.
        assertThat(s.datosHabilidad().indicadoresPorHeroe()).containsOnlyKeys("npc_dota_hero_beta");
        verify(juego, times(2)).obtenerIndicadores(any());
    }

    @Test
    void llamadasConcurrentesAlCalentamientoDescargaUnaSolaVez() throws Exception {
        habilidadesDisponibles();
        ArchivosJuegoClient juego = mock(ArchivosJuegoClient.class);
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        when(juego.obtenerIndicadores(any())).thenAnswer(inv -> {
            dentro.countDown();
            soltar.await(5, TimeUnit.SECONDS);
            return indicadoresDeTodos();
        });
        DatosMetaService s = servicioSinCalentar(juego);
        s.refrescar();
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<?> primera = hilos.submit(s::calentarIndicadores);
            assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();

            s.calentarIndicadores(); // con la primera en vuelo, estas vuelven sin descargar
            s.calentarIndicadores();
            soltar.countDown();
            primera.get(5, TimeUnit.SECONDS);
        } finally {
            hilos.shutdownNow();
        }

        verify(juego, times(1)).obtenerIndicadores(any());
        assertThat(s.datosHabilidad().indicadoresPorHeroe()).hasSize(3);
    }
}
