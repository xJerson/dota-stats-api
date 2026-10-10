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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private static List<Integer> ids(List<Sugerencia> sugerencias) {
        return sugerencias.stream().map(Sugerencia::heroId).toList();
    }

    private static Sugerencia de(List<Sugerencia> sugerencias, int heroId) {
        return sugerencias.stream().filter(s -> s.heroId() == heroId).findFirst().orElseThrow();
    }

    private List<Sugerencia> sugerir(int rol, List<Integer> enemigos, List<Integer> aliados) {
        return motor.sugerir(rol, enemigos, aliados).sugerencias();
    }

    @Test
    void ordenaPorWinrateWilsonYDevuelveSoloLosTresMejores() {
        List<Sugerencia> r = sugerir(1, List.of(), List.of());

        // Gamma (54% con 100 partidas) queda por debajo de Delta (51% con 10000) por la penalización de muestra
        assertThat(ids(r)).containsExactly(1, 2, 4);
        assertThat(r.get(0).puntaje()).isGreaterThan(r.get(1).puntaje());
    }

    @Test
    void sinEnemigosNoPideMatchupsYNoAdvierteNada() {
        ResultadoSugerencias r = motor.sugerir(1, List.of(), List.of());

        assertThat(r.advertencias()).isEmpty();
        verify(servicio, never()).matchupsContra(anyList());
    }

    @Test
    void pideLosMatchupsSoloDeLosEnemigosElegidos() {
        motor.sugerir(1, List.of(9, 3), List.of(2));

        verify(servicio).matchupsContra(List.of(9, 3));
    }

    @Test
    void elMotivoIncluyeElWinrateDelMetaYNoMencionaContrasSinEnemigos() {
        List<Sugerencia> r = sugerir(1, List.of(), List.of());

        assertThat(r.get(0).heroe()).isEqualTo("Alfa");
        assertThat(r.get(0).motivo()).contains("53.0% en el meta").doesNotContain("contra");
    }

    @Test
    void unBuenCounterContraUnEnemigoCambiaElOrden() {
        duelo(1, 9, 100, 40); // Alfa pierde contra el enemigo
        duelo(2, 9, 100, 60); // Beta le gana

        List<Sugerencia> r = sugerir(1, List.of(9), List.of());

        assertThat(r.get(0).heroId()).isEqualTo(2);
        assertThat(r.get(0).motivo()).contains("52.0% en el meta").contains("fuerte contra Enemigo (60%)");
    }

    @Test
    void unDueloConPocasPartidasApenasMuevePuntajeYNoSeMencionaComoCounter() {
        List<Sugerencia> base = sugerir(1, List.of(), List.of());
        duelo(2, 9, 3, 3); // 100% pero solo 3 partidas

        List<Sugerencia> r = sugerir(1, List.of(9), List.of());

        assertThat(ids(r)).isEqualTo(ids(base));
        assertThat(de(r, 2).puntaje()).isCloseTo(de(base, 2).puntaje(), within(0.02));
        assertThat(de(r, 2).motivo()).doesNotContain("contra");
    }

    @Test
    void sinDatosDeDueloElCandidatoPuntuaExactamenteSuMeta() {
        List<Sugerencia> base = sugerir(1, List.of(), List.of());
        duelo(2, 9, 1000, 700); // solo Beta tiene datos; Alfa no

        List<Sugerencia> r = sugerir(1, List.of(9), List.of());

        assertThat(de(r, 1).puntaje()).isCloseTo(de(base, 1).puntaje(), within(1e-12));
        assertThat(de(r, 1).motivo()).doesNotContain("contra");
    }

    @Test
    void tenerDatosDeDueloNeutrosNoPenalizaFrenteAQuienNoLosTiene() {
        List<Sugerencia> base = sugerir(1, List.of(), List.of());
        // Beta (52% en meta) rinde ~lo esperado contra el enemigo con una muestra grande; Delta (51%) no tiene datos.
        duelo(2, 9, 1000, 510);

        List<Sugerencia> r = sugerir(1, List.of(9), List.of());

        assertThat(de(r, 2).puntaje()).isCloseTo(de(base, 2).puntaje(), within(0.005));
        assertThat(de(r, 2).puntaje()).isGreaterThan(de(r, 4).puntaje());
    }

    @Test
    void unMalDueloHundeAlCandidatoPorDebajoDeQuienNoTieneDatos() {
        duelo(2, 9, 1000, 300); // Beta pierde claramente contra el enemigo

        List<Sugerencia> r = sugerir(1, List.of(9), List.of());

        // Beta (52% en meta) cae incluso por debajo de Gamma, que ni siquiera tiene duelo
        assertThat(ids(r)).containsExactly(1, 4, 3);
    }

    @Test
    void promediaElCounterSobreVariosEnemigosYNombraSoloLosFuertes() {
        heroes.put(8, new HeroeMeta(8, "Otro", Set.of("Carry"), 10000, 5000));
        publicar();
        duelo(2, 9, 100, 60);
        duelo(2, 8, 100, 40); // débil contra Otro: no se destaca

        List<Sugerencia> r = sugerir(1, List.of(8, 9), List.of());

        assertThat(de(r, 2).motivo()).contains("fuerte contra Enemigo (60%)").doesNotContain("Otro");
    }

    @Test
    void unEnemigoSinDatosDilutoElCounterEnVezDeIgnorarseONoPremiar() {
        duelo(2, 9, 1000, 700); // Beta destroza a Enemigo; Otro (8) no tiene datos de nadie
        heroes.put(8, new HeroeMeta(8, "Otro", Set.of("Carry"), 10000, 5000));
        publicar();

        double contraUno = de(sugerir(1, List.of(9), List.of()), 2).puntaje();
        double contraDos = de(sugerir(1, List.of(9, 8), List.of()), 2).puntaje();

        // Con un segundo enemigo neutro, la ventaja de Beta se reparte a la mitad
        double meta = de(sugerir(1, List.of(), List.of()), 2).puntaje();
        assertThat(contraDos - meta).isCloseTo((contraUno - meta) / 2, within(1e-9));
    }

    @Test
    void filtraPorElRolDeLaPosicion() {
        assertThat(ids(sugerir(5, List.of(), List.of()))).containsExactly(5, 6);
        assertThat(ids(sugerir(1, List.of(), List.of()))).doesNotContain(5, 6);
    }

    @Test
    void excluyeLosHeroesYaElegidosPorEnemigosYAliados() {
        List<Sugerencia> r = sugerir(1, List.of(1), List.of(2));

        assertThat(ids(r)).doesNotContain(1, 2);
        assertThat(ids(r)).containsExactly(4, 9, 3);
    }

    @Test
    void siFallaElMatchupDeUnEnemigoPuntuaConMetaSoloParaEseEnemigoYAdvierte() {
        duelo(2, 9, 100, 60);
        fallidos.add(9);
        List<Sugerencia> base = sugerir(1, List.of(), List.of());

        ResultadoSugerencias r = motor.sugerir(1, List.of(9), List.of());

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

        ResultadoSugerencias r = motor.sugerir(1, List.of(8, 9), List.of());

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

    private ResultadoSugerencias conPerfil(RendimientoJugador r, List<Integer> enemigos) {
        when(perfiles.obtener(CUENTA)).thenReturn(r);
        return motor.sugerir(1, enemigos, List.of(), CUENTA);
    }

    @Test
    void sinCuentaNoConsultaElPerfilYElResultadoNoCambia() {
        ResultadoSugerencias a = motor.sugerir(1, List.of(), List.of());
        ResultadoSugerencias b = motor.sugerir(1, List.of(), List.of(), null);

        assertThat(b).isEqualTo(a);
        verify(perfiles, never()).obtener(anyLong());
    }

    @Test
    void unHeroeNuncaJugadoPuntuaExactamenteLoMismoQueSinPersonalizar() {
        // Con perfil, un héroe sin partidas vale (1-w)*base + w*meta; sin enemigos base == meta, o sea idéntico.
        List<Sugerencia> base = sugerir(1, List.of(), List.of());

        List<Sugerencia> r = conPerfil(rendimiento(6, 50, 25), List.of()).sugerencias();

        assertThat(de(r, 1).puntaje()).isCloseTo(de(base, 1).puntaje(), within(1e-12));
        assertThat(de(r, 2).puntaje()).isCloseTo(de(base, 2).puntaje(), within(1e-12));
        assertThat(de(r, 1).motivo()).doesNotContain("tu winrate");
    }

    @Test
    void elComponentePersonalEsContraccionHaciaElMetaConFuerzaPriorPersonal() {
        double meta = de(sugerir(1, List.of(), List.of()), 2).puntaje(); // sin enemigos el puntaje es el meta (Wilson)
        // Beta: 40 partidas, 30 victorias. personal = (30 + 20*meta) / (40 + 20). Peso por defecto 0.25.
        double personal = (30 + 20 * meta) / (40 + 20);

        List<Sugerencia> r = conPerfil(rendimiento(2, 40, 30), List.of()).sugerencias();

        assertThat(de(r, 2).puntaje()).isCloseTo(0.75 * meta + 0.25 * personal, within(1e-12));
    }

    @Test
    void conEnemigosLosPesosSeReescalanParaSumarUno() {
        duelo(2, 9, 1000, 700);
        double sinPerfil = de(sugerir(1, List.of(9), List.of()), 2).puntaje(); // 0.4*meta + 0.6*counter
        double meta = de(sugerir(1, List.of(), List.of()), 2).puntaje();
        double personal = (30 + 20 * meta) / (40 + 20);

        List<Sugerencia> r = conPerfil(rendimiento(2, 40, 30), List.of(9)).sugerencias();

        // meta 0.4*0.75=0.30, counter 0.6*0.75=0.45, personal 0.25 -> suman 1
        assertThat(de(r, 2).puntaje()).isCloseTo(0.75 * sinPerfil + 0.25 * personal, within(1e-12));
    }

    @Test
    void unBuenHistorialPersonalSubeAUnHeroeYUnoMaloLoBaja() {
        List<Sugerencia> r = conPerfil(rendimiento(4, 200, 150, 1, 200, 40), List.of()).sugerencias();

        // Sin personalizar el orden es 1, 2, 4; Delta (4) con 75% propio pasa al primer lugar y Alfa (1) con 20% cae
        assertThat(ids(sugerir(1, List.of(), List.of())).get(0)).isEqualTo(1);
        assertThat(ids(r).get(0)).isEqualTo(4);
        assertThat(ids(r).indexOf(1)).isNotEqualTo(0);
    }

    @Test
    void elMotivoMencionaElWinrateYLasPartidasPersonalesDesdeElMinimo() {
        List<Sugerencia> r = conPerfil(rendimiento(1, 40, 26, 2, 5, 3, 4, 4, 4), List.of()).sugerencias();

        assertThat(de(r, 1).motivo()).contains("53.0% en el meta").contains("tu winrate: 65% en 40 partidas");
        assertThat(de(r, 2).motivo()).contains("tu winrate: 60% en 5 partidas"); // justo en el mínimo
        assertThat(de(r, 4).motivo()).doesNotContain("tu winrate"); // 4 < 5: muestra demasiado pequeña para citarla
    }

    @Test
    void elMinimoDePartidasParaCitarElWinrateEsConfigurable() {
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 2, 0.20, 30, null, null);
        MotorSugerenciasPuntaje otro = new MotorSugerenciasPuntaje(servicio, perfiles, props);
        when(perfiles.obtener(CUENTA)).thenReturn(rendimiento(4, 2, 1));

        List<Sugerencia> r = otro.sugerir(1, List.of(), List.of(), CUENTA).sugerencias();

        assertThat(de(r, 4).motivo()).contains("tu winrate: 50% en 2 partidas");
    }

    @Test
    void elPesoYLaFuerzaPersonalesSonConfigurables() {
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.5, 100, 5, 0.20, 30, null, null);
        MotorSugerenciasPuntaje otro = new MotorSugerenciasPuntaje(servicio, perfiles, props);
        when(perfiles.obtener(CUENTA)).thenReturn(rendimiento(2, 100, 100));
        double meta = de(sugerir(1, List.of(), List.of()), 2).puntaje();
        double personal = (100 + 100 * meta) / (100 + 100);

        List<Sugerencia> r = otro.sugerir(1, List.of(), List.of(), CUENTA).sugerencias();

        assertThat(de(r, 2).puntaje()).isCloseTo(0.5 * meta + 0.5 * personal, within(1e-12));
    }

    @Test
    void siOpenDotaFallaAlPedirElPerfilNoPersonalizaYAdvierte() {
        List<Sugerencia> base = sugerir(1, List.of(), List.of());
        when(perfiles.obtener(CUENTA)).thenThrow(new OpenDotaNoDisponibleException("límite"));

        ResultadoSugerencias r = motor.sugerir(1, List.of(), List.of(), CUENTA);

        assertThat(r.sugerencias()).isEqualTo(base);
        assertThat(r.advertencias()).hasSize(1);
        assertThat(r.advertencias().get(0)).contains("personal").contains("OpenDota");
    }

    @Test
    void laAdvertenciaDelPerfilConviveConLasDeMatchups() {
        fallidos.add(9);
        when(perfiles.obtener(CUENTA)).thenThrow(new OpenDotaNoDisponibleException("límite"));

        ResultadoSugerencias r = motor.sugerir(1, List.of(9), List.of(), CUENTA);

        assertThat(r.advertencias()).hasSize(2);
    }

    @Test
    void unPerfilPrivadoPropagaLaExcepcionYNoPideMatchups() {
        when(perfiles.obtener(CUENTA)).thenThrow(new PerfilPrivadoException());

        assertThatThrownBy(() -> motor.sugerir(1, List.of(9), List.of(), CUENTA))
                .isInstanceOf(PerfilPrivadoException.class);
        verify(servicio, never()).matchupsContra(anyList());
    }

    @Test
    void sinDatosCargadosPropagaLaExcepcion() {
        when(servicio.datosParaSugerir()).thenThrow(new DatosNoCargadosException());

        assertThatThrownBy(() -> motor.sugerir(1, List.of(), List.of()))
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

        List<Sugerencia> r = sugerir(2, List.of(), List.of());

        assertThat(ids(r)).containsExactly(1, 4);
    }

    @Test
    void laPosicionUnoYTresUsanSuPropiaLinea() {
        publicar(Map.of(
                1, Map.of(1, 900L, 2, 100L),
                2, Map.of(1, 50L, 2, 100L),
                3, Map.of(1, 50L, 2, 800L)));

        assertThat(ids(sugerir(1, List.of(), List.of()))).containsExactly(1);
        assertThat(ids(sugerir(3, List.of(), List.of()))).containsExactly(2);
    }

    @Test
    void elUmbralDeParticipacionEsInclusivoYSeCalculaSobreLasTresLineas() {
        // Alfa: central = 300 de 1000 = 0.30 exacto -> entra. Beta: 299 de 1000 -> no entra.
        publicar(Map.of(
                1, Map.of(1, 350L, 2, 351L),
                2, Map.of(1, 300L, 2, 299L),
                3, Map.of(1, 350L, 2, 350L)));

        assertThat(ids(sugerir(2, List.of(), List.of()))).containsExactly(1);
    }

    @Test
    void laPosicionUnoExigeAdemasLaEtiquetaCarryPorqueLosSoportesTambienJueganLaLineaSegura() {
        // Epsilon (Support) pasa el filtro de línea con 100% de partidas en la segura, pero no es carry.
        publicar(Map.of(
                1, Map.of(1, 900L, 5, 900L),
                2, Map.of(),
                3, Map.of(1, 100L)));

        assertThat(ids(sugerir(1, List.of(), List.of()))).containsExactly(1);
    }

    @Test
    void unHeroeConPocasPartidasEnLaLineaNoEsCandidatoAunqueSuParticipacionSeaAlta() {
        // Gamma: 100% central pero con 29 partidas (< 30). Alfa tiene 30 y entra.
        publicar(Map.of(
                1, Map.of(),
                2, Map.of(1, 30L, 3, 29L),
                3, Map.of()));

        assertThat(ids(sugerir(2, List.of(), List.of()))).containsExactly(1);
    }

    @Test
    void unHeroeSinNingunaFilaDeLineaNoEsCandidatoSiHayDatosDeLinea() {
        publicar(Map.of(1, Map.of(), 2, Map.of(1, 500L), 3, Map.of()));

        assertThat(ids(sugerir(2, List.of(), List.of()))).containsExactly(1);
    }

    @Test
    void elUmbralYElMinimoDeLineaSonConfigurables() {
        publicar(Map.of(
                1, Map.of(1, 500L, 2, 800L),
                2, Map.of(1, 500L, 2, 10L),
                3, Map.of()));
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 5, 0.01, 5, null, null);
        MotorSugerenciasPuntaje laxo = new MotorSugerenciasPuntaje(servicio, perfiles, props);

        assertThat(ids(laxo.sugerir(2, List.of(), List.of()).sugerencias())).containsExactly(1, 2);
        assertThat(ids(sugerir(2, List.of(), List.of()))).containsExactly(1);
    }

    @Test
    void lasPosicionesCuatroYCincoSiguenUsandoLaEtiquetaSupportSinAdvertencias() {
        publicar(Map.of());

        ResultadoSugerencias r = motor.sugerir(5, List.of(), List.of());

        assertThat(ids(r.sugerencias())).containsExactly(5, 6);
        assertThat(r.advertencias()).isEmpty();
    }

    @Test
    void sinDatosDeLineaUsaLasEtiquetasYAdvierte() {
        publicar(Map.of());

        ResultadoSugerencias r = motor.sugerir(1, List.of(), List.of());

        assertThat(ids(r.sugerencias())).containsExactly(1, 2, 4);
        assertThat(r.advertencias()).hasSize(1);
        assertThat(r.advertencias().get(0)).contains("línea").contains("etiquetas");
    }

    @Test
    void laAdvertenciaDeLineaConviveConLasDeMatchups() {
        publicar(Map.of());
        fallidos.add(9);

        assertThat(motor.sugerir(1, List.of(9), List.of()).advertencias()).hasSize(2);
    }

    @Test
    void laExclusionManualQuitaAlHeroePorNombreSinDistinguirMayusculas() {
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 5, 0.20, 30, null,
                Map.of(1, List.of("alfa ")));
        MotorSugerenciasPuntaje otro = new MotorSugerenciasPuntaje(servicio, perfiles, props);

        assertThat(ids(otro.sugerir(1, List.of(), List.of()).sugerencias())).containsExactly(2, 4, 9);
        assertThat(ids(otro.sugerir(5, List.of(), List.of()).sugerencias())).containsExactly(5, 6);
    }
}
