package com.dotastats.draft;

import com.dotastats.client.Parche;
import com.dotastats.exception.DatosNoCargadosException;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.exception.PerfilPrivadoException;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.Duelo;
import com.dotastats.meta.HeroeMeta;
import com.dotastats.meta.MatchupsEnemigos;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MotorSugerenciasPuntajeTest {

    // ids: 1 Alfa, 2 Beta, 3 Gamma, 4 Delta (carries); 5 Epsilon, 6 Zeta (soportes); 9 Enemigo (carry)
    private final Map<Integer, HeroeMeta> heroes = new HashMap<>();
    // enemigoId -> (candidatoId -> duelo desde la perspectiva del ENEMIGO), como lo entrega OpenDota
    private final Map<Integer, Map<Integer, Duelo>> porEnemigo = new HashMap<>();
    private final Set<Integer> fallidos = new HashSet<>();
    private DatosMetaService servicio;
    private PerfilJugadorService perfiles;
    private MotorSugerenciasPuntaje motor;

    @BeforeEach
    void preparar() {
        // Alfa 53% y Beta 52% con mucha muestra; Gamma 54% pero con solo 100 partidas; Delta 51%.
        heroes.put(1, new HeroeMeta(1, "Alfa", Set.of("Carry"), 10000, 5300));
        heroes.put(2, new HeroeMeta(2, "Beta", Set.of("Carry"), 10000, 5200));
        heroes.put(3, new HeroeMeta(3, "Gamma", Set.of("Carry"), 100, 54));
        heroes.put(4, new HeroeMeta(4, "Delta", Set.of("Carry"), 10000, 5100));
        heroes.put(5, new HeroeMeta(5, "Epsilon", Set.of("Support"), 10000, 5300));
        heroes.put(6, new HeroeMeta(6, "Zeta", Set.of("Support", "Disabler"), 10000, 5000));
        heroes.put(9, new HeroeMeta(9, "Enemigo", Set.of("Carry"), 10000, 5000));

        servicio = mock(DatosMetaService.class);
        perfiles = mock(PerfilJugadorService.class);
        motor = new MotorSugerenciasPuntaje(servicio, perfiles, SugerenciasProperties.porDefecto());
        publicar();
        // Se evalúa en cada llamada, así los tests pueden añadir duelos después de crear el motor.
        when(servicio.matchupsContra(anyList())).thenAnswer(inv -> {
            List<Integer> pedidos = inv.getArgument(0);
            Map<Integer, Map<Integer, Duelo>> disponibles = new HashMap<>();
            Set<Integer> caidos = new HashSet<>();
            for (int id : pedidos) {
                if (fallidos.contains(id)) {
                    caidos.add(id);
                } else {
                    disponibles.put(id, porEnemigo.getOrDefault(id, Map.of()));
                }
            }
            return new MatchupsEnemigos(disponibles, caidos);
        });
    }

    private void publicar() {
        // Por defecto los carries (1-4, 9) juegan siempre la línea segura y los soportes la ofensiva.
        Map<Integer, Long> segura = Map.of(1, 1000L, 2, 1000L, 3, 1000L, 4, 1000L, 9, 1000L);
        Map<Integer, Long> ofensiva = Map.of(5, 1000L, 6, 1000L);
        publicar(Map.of(1, segura, 2, Map.of(), 3, ofensiva));
    }

    private void publicar(Map<Integer, Map<Integer, Long>> partidasPorLinea) {
        DatosMeta datos = new DatosMeta(heroes, new Parche(60, "7.41"), Instant.parse("2026-10-10T12:00:00Z"),
                partidasPorLinea);
        when(servicio.datosParaSugerir()).thenReturn(datos);
    }

    /** El héroe {@code heroe} ganó {@code victorias} de {@code partidas} contra {@code contra}. */
    private void duelo(int heroe, int contra, int partidas, int victorias) {
        porEnemigo.computeIfAbsent(contra, k -> new HashMap<>()).put(heroe, new Duelo(partidas, partidas - victorias));
    }

    /** Enemigos en los slots 3 y 4: oponentes directos de la posición 1, así todos pesan igual (1.0). */
    private static Map<Integer, Integer> directos(int... heroes) {
        Map<Integer, Integer> slots = new java.util.TreeMap<>();
        for (int i = 0; i < heroes.length; i++) {
            slots.put(3 + i, heroes[i]);
        }
        return slots;
    }

    private static List<Integer> ids(List<Sugerencia> sugerencias) {
        return sugerencias.stream().map(Sugerencia::heroId).toList();
    }

    private static Sugerencia de(List<Sugerencia> sugerencias, int heroId) {
        return sugerencias.stream().filter(s -> s.heroId() == heroId).findFirst().orElseThrow();
    }

    private List<Sugerencia> sugerir(int rol, Map<Integer, Integer> enemigos, List<Integer> aliados) {
        return motor.sugerir(rol, enemigos, aliados).sugerencias();
    }

    @Test
    void ordenaPorWinrateWilsonYDevuelveSoloLosTresMejores() {
        List<Sugerencia> r = sugerir(1, Map.of(), List.of());

        // Gamma (54% con 100 partidas) queda por debajo de Delta (51% con 10000) por la penalización de muestra
        assertThat(ids(r)).containsExactly(1, 2, 4);
        assertThat(r.get(0).puntaje()).isGreaterThan(r.get(1).puntaje());
    }

    @Test
    void sinEnemigosNoPideMatchupsYNoAdvierteNada() {
        ResultadoSugerencias r = motor.sugerir(1, Map.of(), List.of());

        assertThat(r.advertencias()).isEmpty();
        verify(servicio, never()).matchupsContra(anyList());
    }

    @Test
    void pideLosMatchupsSoloDeLosEnemigosElegidos() {
        motor.sugerir(1, directos(9, 3), List.of(2));

        verify(servicio).matchupsContra(List.of(9, 3));
    }

    @Test
    void elMotivoIncluyeElWinrateDelMetaYNoMencionaContrasSinEnemigos() {
        List<Sugerencia> r = sugerir(1, Map.of(), List.of());

        assertThat(r.get(0).heroe()).isEqualTo("Alfa");
        assertThat(r.get(0).motivo()).contains("53.0% en el meta").doesNotContain("contra");
    }

    @Test
    void unBuenCounterContraUnEnemigoCambiaElOrden() {
        duelo(1, 9, 100, 40); // Alfa pierde contra el enemigo
        duelo(2, 9, 100, 60); // Beta le gana

        List<Sugerencia> r = sugerir(1, directos(9), List.of());

        assertThat(r.get(0).heroId()).isEqualTo(2);
        assertThat(r.get(0).motivo()).contains("52.0% en el meta").contains("fuerte contra Enemigo (60%)");
    }

    @Test
    void unDueloConPocasPartidasApenasMuevePuntajeYNoSeMencionaComoCounter() {
        List<Sugerencia> base = sugerir(1, Map.of(), List.of());
        duelo(2, 9, 3, 3); // 100% pero solo 3 partidas

        List<Sugerencia> r = sugerir(1, directos(9), List.of());

        assertThat(ids(r)).isEqualTo(ids(base));
        assertThat(de(r, 2).puntaje()).isCloseTo(de(base, 2).puntaje(), within(0.02));
        assertThat(de(r, 2).motivo()).doesNotContain("fuerte contra");
    }

    @Test
    void sinDatosDeDueloElCandidatoPuntuaExactamenteSuMeta() {
        List<Sugerencia> base = sugerir(1, Map.of(), List.of());
        duelo(2, 9, 1000, 700); // solo Beta tiene datos; Alfa no

        List<Sugerencia> r = sugerir(1, directos(9), List.of());

        assertThat(de(r, 1).puntaje()).isCloseTo(de(base, 1).puntaje(), within(1e-12));
        assertThat(de(r, 1).motivo()).doesNotContain("fuerte contra");
    }

    @Test
    void tenerDatosDeDueloNeutrosNoPenalizaFrenteAQuienNoLosTiene() {
        List<Sugerencia> base = sugerir(1, Map.of(), List.of());
        // Beta (52% en meta) rinde ~lo esperado contra el enemigo con una muestra grande; Delta (51%) no tiene datos.
        duelo(2, 9, 1000, 510);

        List<Sugerencia> r = sugerir(1, directos(9), List.of());

        assertThat(de(r, 2).puntaje()).isCloseTo(de(base, 2).puntaje(), within(0.005));
        assertThat(de(r, 2).puntaje()).isGreaterThan(de(r, 4).puntaje());
    }

    @Test
    void unMalDueloHundeAlCandidatoPorDebajoDeQuienNoTieneDatos() {
        duelo(2, 9, 1000, 300); // Beta pierde claramente contra el enemigo

        List<Sugerencia> r = sugerir(1, directos(9), List.of());

        // Beta (52% en meta) cae incluso por debajo de Gamma, que ni siquiera tiene duelo
        assertThat(ids(r)).containsExactly(1, 4, 3);
    }

    @Test
    void promediaElCounterSobreVariosEnemigosYNombraSoloLosFuertes() {
        heroes.put(8, new HeroeMeta(8, "Otro", Set.of("Carry"), 10000, 5000));
        publicar();
        duelo(2, 9, 100, 60);
        duelo(2, 8, 100, 40); // débil contra Otro: no se destaca

        List<Sugerencia> r = sugerir(1, directos(8, 9), List.of());

        assertThat(de(r, 2).motivo()).contains("fuerte contra Enemigo (60%)").doesNotContain("Otro");
    }

    @Test
    void unEnemigoSinDatosDilutoElCounterEnVezDeIgnorarseONoPremiar() {
        duelo(2, 9, 1000, 700); // Beta destroza a Enemigo; Otro (8) no tiene datos de nadie
        heroes.put(8, new HeroeMeta(8, "Otro", Set.of("Carry"), 10000, 5000));
        publicar();

        double contraUno = de(sugerir(1, directos(9), List.of()), 2).puntaje();
        double contraDos = de(sugerir(1, directos(9, 8), List.of()), 2).puntaje();

        // Con un segundo enemigo neutro, la ventaja de Beta se reparte a la mitad
        double meta = de(sugerir(1, Map.of(), List.of()), 2).puntaje();
        assertThat(contraDos - meta).isCloseTo((contraUno - meta) / 2, within(1e-9));
    }

    @Test
    void filtraPorElRolDeLaPosicion() {
        assertThat(ids(sugerir(5, Map.of(), List.of()))).containsExactly(5, 6);
        assertThat(ids(sugerir(1, Map.of(), List.of()))).doesNotContain(5, 6);
    }

    @Test
    void excluyeLosHeroesYaElegidosPorEnemigosYAliados() {
        List<Sugerencia> r = sugerir(1, directos(1), List.of(2));

        assertThat(ids(r)).doesNotContain(1, 2);
        assertThat(ids(r)).containsExactly(4, 9, 3);
    }

    @Test
    void siFallaElMatchupDeUnEnemigoPuntuaConMetaSoloParaEseEnemigoYAdvierte() {
        duelo(2, 9, 100, 60);
        fallidos.add(9);
        List<Sugerencia> base = sugerir(1, Map.of(), List.of());

        ResultadoSugerencias r = motor.sugerir(1, directos(9), List.of());

        assertThat(r.sugerencias().get(0).puntaje()).isEqualTo(base.get(0).puntaje());
        assertThat(ids(r.sugerencias())).isEqualTo(ids(base));
        assertThat(r.advertencias()).hasSize(1);
        assertThat(r.advertencias().get(0)).contains("Enemigo").contains("solo el meta");
    }

    @Test
    void conUnEnemigoFallidoYOtroBienSoloElFallidoPuntuaConMeta() {
        heroes.put(8, new HeroeMeta(8, "Otro", Set.of("Carry"), 10000, 5000));
        publicar();
        duelo(2, 8, 100, 60);
        fallidos.add(9);

        ResultadoSugerencias r = motor.sugerir(1, directos(8, 9), List.of());

        assertThat(de(r.sugerencias(), 2).motivo()).contains("fuerte contra Otro (60%)");
        assertThat(r.advertencias()).hasSize(1);
        assertThat(r.advertencias().get(0)).contains("Enemigo").doesNotContain("Otro");
    }

    // --- Personalización con el historial del jugador (steamId) ---

    private static final long CUENTA = 555L;

    /** Triples (heroId, partidas, victorias) del historial del jugador. */
    private static RendimientoJugador rendimiento(int... heroePartidasVictorias) {
        Map<Integer, Duelo> porHeroe = new HashMap<>();
        for (int i = 0; i < heroePartidasVictorias.length; i += 3) {
            porHeroe.put(heroePartidasVictorias[i],
                    new Duelo(heroePartidasVictorias[i + 1], heroePartidasVictorias[i + 2]));
        }
        return new RendimientoJugador(porHeroe);
    }

    private ResultadoSugerencias conPerfil(RendimientoJugador r, Map<Integer, Integer> enemigos) {
        when(perfiles.obtener(CUENTA)).thenReturn(r);
        return motor.sugerir(1, enemigos, List.of(), CUENTA);
    }

    @Test
    void sinCuentaNoConsultaElPerfilYElResultadoNoCambia() {
        ResultadoSugerencias a = motor.sugerir(1, Map.of(), List.of());
        ResultadoSugerencias b = motor.sugerir(1, Map.of(), List.of(), null);

        assertThat(b).isEqualTo(a);
        verify(perfiles, never()).obtener(anyLong());
    }

    @Test
    void unHeroeNuncaJugadoPuntuaExactamenteLoMismoQueSinPersonalizar() {
        // Con perfil, un héroe sin partidas vale (1-w)*base + w*meta; sin enemigos base == meta, o sea idéntico.
        List<Sugerencia> base = sugerir(1, Map.of(), List.of());

        List<Sugerencia> r = conPerfil(rendimiento(6, 50, 25), Map.of()).sugerencias();

        assertThat(de(r, 1).puntaje()).isCloseTo(de(base, 1).puntaje(), within(1e-12));
        assertThat(de(r, 2).puntaje()).isCloseTo(de(base, 2).puntaje(), within(1e-12));
        assertThat(de(r, 1).motivo()).doesNotContain("tu winrate");
    }

    @Test
    void elComponentePersonalEsContraccionHaciaElMetaConFuerzaPriorPersonal() {
        double meta = de(sugerir(1, Map.of(), List.of()), 2).puntaje(); // sin enemigos el puntaje es el meta (Wilson)
        // Beta: 40 partidas, 30 victorias. personal = (30 + 20*meta) / (40 + 20). Peso por defecto 0.25.
        double personal = (30 + 20 * meta) / (40 + 20);

        List<Sugerencia> r = conPerfil(rendimiento(2, 40, 30), Map.of()).sugerencias();

        assertThat(de(r, 2).puntaje()).isCloseTo(0.75 * meta + 0.25 * personal, within(1e-12));
    }

    @Test
    void conEnemigosLosPesosSeReescalanParaSumarUno() {
        duelo(2, 9, 1000, 700);
        double sinPerfil = de(sugerir(1, directos(9), List.of()), 2).puntaje(); // 0.4*meta + 0.6*counter
        double meta = de(sugerir(1, Map.of(), List.of()), 2).puntaje();
        double personal = (30 + 20 * meta) / (40 + 20);

        List<Sugerencia> r = conPerfil(rendimiento(2, 40, 30), directos(9)).sugerencias();

        // meta 0.4*0.75=0.30, counter 0.6*0.75=0.45, personal 0.25 -> suman 1
        assertThat(de(r, 2).puntaje()).isCloseTo(0.75 * sinPerfil + 0.25 * personal, within(1e-12));
    }

    @Test
    void unBuenHistorialPersonalSubeAUnHeroeYUnoMaloLoBaja() {
        List<Sugerencia> r = conPerfil(rendimiento(4, 200, 150, 1, 200, 40), Map.of()).sugerencias();

        // Sin personalizar el orden es 1, 2, 4; Delta (4) con 75% propio pasa al primer lugar y Alfa (1) con 20% cae
        assertThat(ids(sugerir(1, Map.of(), List.of())).get(0)).isEqualTo(1);
        assertThat(ids(r).get(0)).isEqualTo(4);
        assertThat(ids(r).indexOf(1)).isNotEqualTo(0);
    }

    @Test
    void elMotivoMencionaElWinrateYLasPartidasPersonalesDesdeElMinimo() {
        List<Sugerencia> r = conPerfil(rendimiento(1, 40, 26, 2, 5, 3, 4, 4, 4), Map.of()).sugerencias();

        assertThat(de(r, 1).motivo()).contains("53.0% en el meta").contains("tu winrate: 65% en 40 partidas");
        assertThat(de(r, 2).motivo()).contains("tu winrate: 60% en 5 partidas"); // justo en el mínimo
        assertThat(de(r, 4).motivo()).doesNotContain("tu winrate"); // 4 < 5: muestra demasiado pequeña para citarla
    }

    @Test
    void elMinimoDePartidasParaCitarElWinrateEsConfigurable() {
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 2, 0.20, 30, 1.0, 0.5, null, null, null);
        MotorSugerenciasPuntaje otro = new MotorSugerenciasPuntaje(servicio, perfiles, props);
        when(perfiles.obtener(CUENTA)).thenReturn(rendimiento(4, 2, 1));

        List<Sugerencia> r = otro.sugerir(1, Map.of(), List.of(), CUENTA).sugerencias();

        assertThat(de(r, 4).motivo()).contains("tu winrate: 50% en 2 partidas");
    }

    @Test
    void elPesoYLaFuerzaPersonalesSonConfigurables() {
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.5, 100, 5, 0.20, 30, 1.0, 0.5, null, null, null);
        MotorSugerenciasPuntaje otro = new MotorSugerenciasPuntaje(servicio, perfiles, props);
        when(perfiles.obtener(CUENTA)).thenReturn(rendimiento(2, 100, 100));
        double meta = de(sugerir(1, Map.of(), List.of()), 2).puntaje();
        double personal = (100 + 100 * meta) / (100 + 100);

        List<Sugerencia> r = otro.sugerir(1, Map.of(), List.of(), CUENTA).sugerencias();

        assertThat(de(r, 2).puntaje()).isCloseTo(0.5 * meta + 0.5 * personal, within(1e-12));
    }

    @Test
    void siOpenDotaFallaAlPedirElPerfilNoPersonalizaYAdvierte() {
        List<Sugerencia> base = sugerir(1, Map.of(), List.of());
        when(perfiles.obtener(CUENTA)).thenThrow(new OpenDotaNoDisponibleException("límite"));

        ResultadoSugerencias r = motor.sugerir(1, Map.of(), List.of(), CUENTA);

        assertThat(r.sugerencias()).isEqualTo(base);
        assertThat(r.advertencias()).hasSize(1);
        assertThat(r.advertencias().get(0)).contains("personal").contains("OpenDota");
    }

    @Test
    void laAdvertenciaDelPerfilConviveConLasDeMatchups() {
        fallidos.add(9);
        when(perfiles.obtener(CUENTA)).thenThrow(new OpenDotaNoDisponibleException("límite"));

        ResultadoSugerencias r = motor.sugerir(1, directos(9), List.of(), CUENTA);

        assertThat(r.advertencias()).hasSize(2);
    }

    @Test
    void unPerfilPrivadoPropagaLaExcepcionYNoPideMatchups() {
        when(perfiles.obtener(CUENTA)).thenThrow(new PerfilPrivadoException());

        assertThatThrownBy(() -> motor.sugerir(1, directos(9), List.of(), CUENTA))
                .isInstanceOf(PerfilPrivadoException.class);
        verify(servicio, never()).matchupsContra(anyList());
    }

    @Test
    void sinDatosCargadosPropagaLaExcepcion() {
        when(servicio.datosParaSugerir()).thenThrow(new DatosNoCargadosException());

        assertThatThrownBy(() -> motor.sugerir(1, Map.of(), List.of()))
                .isInstanceOf(DatosNoCargadosException.class);
    }

    // --- Filtro por posición con datos de línea (/scenarios/laneRoles) ---

    @Test
    void laPosicionDosUsaLaLineaCentralYNoLasEtiquetas() {
        // Alfa juega 80% central y Delta 31%; Beta (también "Carry" por etiqueta) solo 5% central: no es mid.
        publicar(Map.of(
                1, Map.of(1, 100L, 2, 900L, 4, 800L),
                2, Map.of(1, 800L, 2, 50L, 4, 400L),
                3, Map.of(1, 100L, 2, 50L, 4, 100L)));

        List<Sugerencia> r = sugerir(2, Map.of(), List.of());

        assertThat(ids(r)).containsExactly(1, 4);
    }

    @Test
    void laPosicionUnoYTresUsanSuPropiaLinea() {
        publicar(Map.of(
                1, Map.of(1, 900L, 2, 100L),
                2, Map.of(1, 50L, 2, 100L),
                3, Map.of(1, 50L, 2, 800L)));

        assertThat(ids(sugerir(1, Map.of(), List.of()))).containsExactly(1);
        assertThat(ids(sugerir(3, Map.of(), List.of()))).containsExactly(2);
    }

    @Test
    void elUmbralDeParticipacionEsInclusivoYSeCalculaSobreLasTresLineas() {
        // Alfa: central = 300 de 1000 = 0.30 exacto -> entra. Beta: 299 de 1000 -> no entra.
        publicar(Map.of(
                1, Map.of(1, 350L, 2, 351L),
                2, Map.of(1, 300L, 2, 299L),
                3, Map.of(1, 350L, 2, 350L)));

        assertThat(ids(sugerir(2, Map.of(), List.of()))).containsExactly(1);
    }

    @Test
    void laPosicionUnoExigeAdemasLaEtiquetaCarryPorqueLosSoportesTambienJueganLaLineaSegura() {
        // Epsilon (Support) pasa el filtro de línea con 100% de partidas en la segura, pero no es carry.
        publicar(Map.of(
                1, Map.of(1, 900L, 5, 900L),
                2, Map.of(),
                3, Map.of(1, 100L)));

        assertThat(ids(sugerir(1, Map.of(), List.of()))).containsExactly(1);
    }

    @Test
    void unHeroeConPocasPartidasEnLaLineaNoEsCandidatoAunqueSuParticipacionSeaAlta() {
        // Gamma: 100% central pero con 29 partidas (< 30). Alfa tiene 30 y entra.
        publicar(Map.of(
                1, Map.of(),
                2, Map.of(1, 30L, 3, 29L),
                3, Map.of()));

        assertThat(ids(sugerir(2, Map.of(), List.of()))).containsExactly(1);
    }

    @Test
    void unHeroeSinNingunaFilaDeLineaNoEsCandidatoSiHayDatosDeLinea() {
        publicar(Map.of(1, Map.of(), 2, Map.of(1, 500L), 3, Map.of()));

        assertThat(ids(sugerir(2, Map.of(), List.of()))).containsExactly(1);
    }

    @Test
    void elUmbralYElMinimoDeLineaSonConfigurables() {
        publicar(Map.of(
                1, Map.of(1, 500L, 2, 800L),
                2, Map.of(1, 500L, 2, 10L),
                3, Map.of()));
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 5, 0.01, 5, 1.0, 0.5, null, null, null);
        MotorSugerenciasPuntaje laxo = new MotorSugerenciasPuntaje(servicio, perfiles, props);

        assertThat(ids(laxo.sugerir(2, Map.of(), List.of()).sugerencias())).containsExactly(1, 2);
        assertThat(ids(sugerir(2, Map.of(), List.of()))).containsExactly(1);
    }

    @Test
    void lasPosicionesCuatroYCincoSiguenUsandoLaEtiquetaSupportSinAdvertencias() {
        publicar(Map.of());

        ResultadoSugerencias r = motor.sugerir(5, Map.of(), List.of());

        assertThat(ids(r.sugerencias())).containsExactly(5, 6);
        assertThat(r.advertencias()).isEmpty();
    }

    @Test
    void sinDatosDeLineaUsaLasEtiquetasYAdvierte() {
        publicar(Map.of());

        ResultadoSugerencias r = motor.sugerir(1, Map.of(), List.of());

        assertThat(ids(r.sugerencias())).containsExactly(1, 2, 4);
        assertThat(r.advertencias()).hasSize(1);
        assertThat(r.advertencias().get(0)).contains("línea").contains("etiquetas");
    }

    @Test
    void laAdvertenciaDeLineaConviveConLasDeMatchups() {
        publicar(Map.of());
        fallidos.add(9);

        assertThat(motor.sugerir(1, directos(9), List.of()).advertencias()).hasSize(2);
    }

    @Test
    void laExclusionManualQuitaAlHeroePorNombreSinDistinguirMayusculas() {
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 5, 0.20, 30, 1.0, 0.5, null,
                Map.of(1, List.of("alfa ")), null);
        MotorSugerenciasPuntaje otro = new MotorSugerenciasPuntaje(servicio, perfiles, props);

        assertThat(ids(otro.sugerir(1, Map.of(), List.of()).sugerencias())).containsExactly(2, 4, 9);
        assertThat(ids(otro.sugerir(5, Map.of(), List.of()).sugerencias())).containsExactly(5, 6);
    }

    // --- Listas curadas de héroes para las posiciones 4 y 5 ---

    private MotorSugerenciasPuntaje motorConListas(Map<Integer, List<String>> listas) {
        return motorConListas(listas, null);
    }

    private MotorSugerenciasPuntaje motorConListas(Map<Integer, List<String>> listas,
                                                   Map<Integer, List<String>> excluidos) {
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 5, 0.30, 30, 1.0, 0.5,
                null, excluidos, listas);
        return new MotorSugerenciasPuntaje(servicio, perfiles, props);
    }

    /** Héroes con etiquetas "sueltas" como las de OpenDota: 7 Iniciador (sin Support), 8 sin etiquetas. */
    private void heroesDeApoyoExtra() {
        heroes.put(7, new HeroeMeta(7, "Eta", Set.of("Initiator", "Durable"), 10000, 5400));
        heroes.put(8, new HeroeMeta(8, "Theta", Set.of(), 10000, 4900));
        publicar();
    }

    @Test
    void sinListaLaPosicionCuatroSigueLaReglaDeEtiquetas() {
        heroesDeApoyoExtra();

        // Support o Initiator: Eta (Initiator) entra aunque no sea un soporte "de verdad"
        assertThat(ids(sugerir(4, Map.of(), List.of()))).containsExactly(7, 5, 6);
    }

    @Test
    void laListaDeLaPosicionSustituyeALasEtiquetas() {
        heroesDeApoyoExtra();
        MotorSugerenciasPuntaje otro = motorConListas(Map.of(4, List.of("Zeta", "Theta")));

        // Epsilon (Support) y Eta (Initiator) tienen mejor meta pero no están en la lista; Theta no tiene etiquetas
        assertThat(ids(otro.sugerir(4, Map.of(), List.of()).sugerencias())).containsExactly(6, 8);
    }

    @Test
    void laListaNoExigeEtiquetaNiAdvierteAunqueFaltenLosDatosDeLinea() {
        heroesDeApoyoExtra();
        publicar(Map.of());
        MotorSugerenciasPuntaje otro = motorConListas(Map.of(5, List.of("Theta")));

        ResultadoSugerencias r = otro.sugerir(5, Map.of(), List.of());

        assertThat(ids(r.sugerencias())).containsExactly(8);
        assertThat(r.advertencias()).isEmpty();
    }

    @Test
    void unaListaVaciaOAusenteCaeALasEtiquetas() {
        heroesDeApoyoExtra();

        assertThat(ids(motorConListas(Map.of(4, List.of())).sugerir(4, Map.of(), List.of()).sugerencias()))
                .containsExactly(7, 5, 6);
        assertThat(ids(motorConListas(Map.of(4, List.of(" ", ""))).sugerir(4, Map.of(), List.of()).sugerencias()))
                .containsExactly(7, 5, 6);
        // la lista de la 4 no afecta a la 5, que no tiene la suya
        assertThat(ids(motorConListas(Map.of(4, List.of("Theta"))).sugerir(5, Map.of(), List.of()).sugerencias()))
                .containsExactly(5, 6);
    }

    @Test
    void laListaIgnoraMayusculasAcentosYEspacios() {
        heroes.put(10, new HeroeMeta(10, "Éta Ñu-Mago", Set.of(), 10000, 5000));
        publicar();
        MotorSugerenciasPuntaje otro = motorConListas(Map.of(4, List.of("  ETA nu mago ", "zETA")));

        assertThat(ids(otro.sugerir(4, Map.of(), List.of()).sugerencias())).containsExactlyInAnyOrder(6, 10);
    }

    @Test
    void laExclusionManualSigueAplicandoseSobreLaLista() {
        MotorSugerenciasPuntaje otro = motorConListas(Map.of(4, List.of("Epsilon", "Zeta")),
                Map.of(4, List.of("epsilon")));

        assertThat(ids(otro.sugerir(4, Map.of(), List.of()).sugerencias())).containsExactly(6);
    }

    @Test
    void lasListasDeLas4y5NoTocanLasPosiciones1a3() {
        heroesDeApoyoExtra();
        MotorSugerenciasPuntaje otro = motorConListas(Map.of(4, List.of("Theta"), 5, List.of("Theta")));

        for (int rol = 1; rol <= 3; rol++) {
            assertThat(otro.sugerir(rol, Map.of(), List.of()))
                    .as("posición %d", rol)
                    .isEqualTo(motor.sugerir(rol, Map.of(), List.of()));
        }
    }

    @Test
    void losNombresDeLaListaQueNoExistenEnElCatalogoSeAvisanUnaVezPorWarn() {
        Logger logger = (Logger) LoggerFactory.getLogger(MotorSugerenciasPuntaje.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MotorSugerenciasPuntaje otro = motorConListas(Map.of(4, List.of("Zeta", "Fantasma Uno"),
                    5, List.of("epsilon", "Fantasma Dos")));

            assertThat(otro.nombresDesconocidos(servicio.datosParaSugerir()))
                    .containsExactly("posición 4: Fantasma Uno", "posición 5: Fantasma Dos");
            otro.sugerir(4, Map.of(), List.of());
            otro.sugerir(5, Map.of(), List.of());

            List<ILoggingEvent> avisos = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
            assertThat(avisos).hasSize(1);
            assertThat(avisos.get(0).getFormattedMessage()).contains("Fantasma Uno").contains("Fantasma Dos");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void siTodosLosNombresDeLaListaExistenNoHayAvisos() {
        Logger logger = (Logger) LoggerFactory.getLogger(MotorSugerenciasPuntaje.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MotorSugerenciasPuntaje otro = motorConListas(Map.of(4, List.of("Zeta")));

            otro.sugerir(4, Map.of(), List.of());

            assertThat(otro.nombresDesconocidos(servicio.datosParaSugerir())).isEmpty();
            assertThat(appender.list).noneMatch(e -> e.getLevel() == Level.WARN);
        } finally {
            logger.detachAppender(appender);
        }
    }

    // --- Peso por posición del enemigo (oponentes directos de la línea) ---

    private MotorSugerenciasPuntaje motorConPesos(double directo, double otros) {
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 5, 0.30, 30,
                directo, otros, null, null, null);
        return new MotorSugerenciasPuntaje(servicio, perfiles, props);
    }

    /** Dos enemigos extra: 8 "Otro" y 9 "Enemigo" con duelos cruzados para Alfa (1) y Beta (2). */
    private void duelosCruzados() {
        heroes.put(8, new HeroeMeta(8, "Otro", Set.of("Carry"), 10000, 5000));
        publicar();
        duelo(1, 9, 1000, 700); // Alfa destroza a Enemigo y pierde contra Otro
        duelo(1, 8, 1000, 300);
        duelo(2, 8, 1000, 700); // Beta, al revés
        duelo(2, 9, 1000, 300);
    }

    @Test
    void losOponentesDirectosDependenDeLaLineaDeLaPosicion() {
        assertThat(MotorSugerenciasPuntaje.oponentesDirectos(1)).containsExactlyInAnyOrder(3, 4);
        assertThat(MotorSugerenciasPuntaje.oponentesDirectos(5)).containsExactlyInAnyOrder(3, 4);
        assertThat(MotorSugerenciasPuntaje.oponentesDirectos(2)).containsExactly(2);
        assertThat(MotorSugerenciasPuntaje.oponentesDirectos(3)).containsExactlyInAnyOrder(1, 5);
        assertThat(MotorSugerenciasPuntaje.oponentesDirectos(4)).containsExactlyInAnyOrder(1, 5);
    }

    @Test
    void elMismoParDeEnemigosEnOtroSlotCambiaElGanador() {
        duelosCruzados();

        // Mid (posición 2): el oponente directo es el slot 2 del rival.
        List<Sugerencia> contraEnemigoEnMid = sugerir(2, Map.of(2, 9, 1, 8), List.of());
        List<Sugerencia> contraOtroEnMid = sugerir(2, Map.of(2, 8, 1, 9), List.of());

        assertThat(ids(contraEnemigoEnMid).get(0)).isEqualTo(1);
        assertThat(ids(contraOtroEnMid).get(0)).isEqualTo(2);
    }

    @Test
    void elCounterEsElPromedioPonderadoDeLasEstimacionesPorEnemigo() {
        duelosCruzados();
        double meta = de(sugerir(2, Map.of(), List.of()), 2).puntaje();
        double contraEnemigo = (300 + 100 * meta) / (1000 + 100); // Beta vs 9, enemigo directo (peso 1.0)
        double contraOtro = (700 + 100 * meta) / (1000 + 100);    // Beta vs 8, en otra línea (peso 0.5)
        double counter = (1.0 * contraEnemigo + 0.5 * contraOtro) / 1.5;

        double puntaje = de(sugerir(2, Map.of(2, 9, 1, 8), List.of()), 2).puntaje();

        assertThat(puntaje).isCloseTo(0.4 * meta + 0.6 * counter, within(1e-12));
    }

    @Test
    void losPesosSonConfigurables() {
        duelosCruzados();
        MotorSugerenciasPuntaje soloDirecto = motorConPesos(1.0, 0.0);
        double meta = de(sugerir(2, Map.of(), List.of()), 2).puntaje();
        double contraEnemigo = (700 + 100 * meta) / (1000 + 100); // Beta vs 8, que ahora ocupa el slot 2

        double puntaje = de(soloDirecto.sugerir(2, Map.of(2, 8, 1, 9), List.of()).sugerencias(), 2).puntaje();

        // Con peso 0 para los demás, solo cuenta el enemigo de la línea
        assertThat(puntaje).isCloseTo(0.4 * meta + 0.6 * contraEnemigo, within(1e-12));
    }

    @Test
    void siNingunEnemigoTienePesoElPuntajeEsElMeta() {
        duelosCruzados();
        MotorSugerenciasPuntaje soloDirecto = motorConPesos(1.0, 0.0);
        double meta = de(sugerir(2, Map.of(), List.of()), 2).puntaje();

        // Posición 2: el enemigo del slot 5 no es oponente directo y pesa 0
        double puntaje = de(soloDirecto.sugerir(2, Map.of(5, 9), List.of()).sugerencias(), 2).puntaje();

        assertThat(puntaje).isCloseTo(meta, within(1e-12));
    }

    // --- Motivos honestos ---

    @Test
    void unOponenteDirectoSinDatosSuficientesSeNombraEnElMotivo() {
        heroes.put(8, new HeroeMeta(8, "Otro", Set.of("Carry"), 10000, 5000));
        publicar();
        duelo(2, 9, 3, 3); // solo 3 partidas
        duelo(2, 8, 1000, 700); // hay datos reales contra Otro, así que no es un ranking solo por meta

        String motivo = de(sugerir(1, Map.of(3, 9, 4, 8), List.of()), 2).motivo();

        assertThat(motivo).contains("fuerte contra Otro (70%)").contains("sin datos suficientes contra Enemigo")
                .doesNotContain("según el meta");
    }

    @Test
    void unEnemigoQueNoEsOponenteDirectoNoApareceComoFaltaDeDatos() {
        heroes.put(8, new HeroeMeta(8, "Otro", Set.of("Carry"), 10000, 5000));
        publicar();
        duelo(2, 8, 1000, 700);

        // Posición 1: el slot 1 enemigo no es oponente directo; el 9 sin datos no se nombra
        String motivo = de(sugerir(1, Map.of(3, 8, 1, 9), List.of()), 2).motivo();

        assertThat(motivo).contains("fuerte contra Otro (70%)").doesNotContain("Enemigo");
    }

    @Test
    void nombraALosDosOponentesDirectosSinDatosPeroNoALosDemas() {
        heroes.put(7, new HeroeMeta(7, "Tercero", Set.of("Carry"), 10000, 5000));
        heroes.put(8, new HeroeMeta(8, "Otro", Set.of("Carry"), 10000, 5000));
        publicar();
        duelo(2, 7, 1000, 700); // algún dato usable (contra un enemigo no directo) evita el modo solo-meta

        // Posición 1: oponentes directos = slots 3 y 4; el slot 5 no cuenta como falta de datos
        String motivo = de(sugerir(1, Map.of(3, 9, 4, 8, 5, 7), List.of()), 2).motivo();

        assertThat(motivo).contains("sin datos suficientes contra Enemigo y Otro")
                .doesNotContain("y Tercero").contains("fuerte contra Tercero (70%)");
    }

    @Test
    void sinDatosDeDueloSuficientesElMotivoDiceQueEsUnRankingPorMeta() {
        duelo(2, 9, 3, 3); // muestra insuficiente

        List<Sugerencia> r = sugerir(1, Map.of(3, 9), List.of());

        assertThat(de(r, 2).motivo()).contains("según el meta, sin datos de duelo suficientes")
                .doesNotContain("sin datos suficientes contra").doesNotContain("fuerte contra");
        assertThat(de(r, 1).motivo()).contains("según el meta, sin datos de duelo suficientes");
    }

    @Test
    void sinEnemigosElMotivoNoHablaDeDuelos() {
        assertThat(de(sugerir(1, Map.of(), List.of()), 1).motivo()).doesNotContain("duelo").doesNotContain("datos");
    }
}
