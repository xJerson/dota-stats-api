package com.dotastats.analizadores.habilidad;

import com.dotastats.analizadores.ContextoDraft;
import com.dotastats.analizadores.habilidad.dependencias.Contrarresto;
import com.dotastats.analizadores.habilidad.dependencias.Efecto;
import com.dotastats.analizadores.habilidad.dependencias.Mecanismo;
import com.dotastats.analizadores.habilidad.dependencias.RepositorioDependencias;
import com.dotastats.analizadores.habilidad.dependencias.TablaContrarrestos;
import com.dotastats.client.Parche;
import com.dotastats.draft.Sugerencia;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.HeroeMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalizadorHabilidadTest {

    // Lion(26)=counter de Storm Spirit(6, alta); Shadow Shaman(27)=counter de Queen of Pain(39, media);
    // Pugna(14)=counter de Zeus(22, media); Abaddon(102)=counter de Lion y Shadow Shaman (media).
    private static final HeroeMeta LION = new HeroeMeta(26, "Lion", Set.of(), 1000, 500);
    private static final HeroeMeta SHADOW_SHAMAN = new HeroeMeta(27, "Shadow Shaman", Set.of(), 1000, 500);
    private static final HeroeMeta STORM_SPIRIT = new HeroeMeta(6, "Storm Spirit", Set.of(), 1000, 500);
    private static final HeroeMeta QUEEN_OF_PAIN = new HeroeMeta(39, "Queen of Pain", Set.of(), 1000, 500);
    private static final HeroeMeta OTRO = new HeroeMeta(999, "Otro", Set.of(), 1000, 500);

    private RepositorioReglas repositorio;
    private CatalogoHabilidades catalogo;
    private RepositorioDependencias dependencias;
    private TablaContrarrestos tabla;
    private DatosMeta datos;
    private AnalizadorHabilidad analizador;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioReglas.class);
        catalogo = mock(CatalogoHabilidades.class);
        // Por defecto el catálogo automático está disponible pero sin perfiles: así los tests existentes (solo
        // reglas a mano) no se ven afectados por el nuevo mecanismo. Los tests de interacciones automáticas
        // sobrescriben esto explícitamente.
        dependencias = mock(RepositorioDependencias.class); // por defecto ningún enemigo tiene dependencias curadas
        tabla = mock(TablaContrarrestos.class);
        when(tabla.disponible(any())).thenReturn(true);
        when(catalogo.disponible(any())).thenReturn(true);
        when(catalogo.perfilDe(anyInt(), any())).thenReturn(Optional.empty());
        datos = new DatosMeta(
                Map.of(26, LION, 27, SHADOW_SHAMAN, 6, STORM_SPIRIT, 39, QUEEN_OF_PAIN, 999, OTRO),
                new Parche(60, "7.41"), Instant.parse("2026-10-10T12:00:00Z"));
        analizador = new AnalizadorHabilidad(repositorio, new HabilidadProperties(1.0, 0.5, 0.25,
                "classpath:reglas-habilidades.json", null, null), catalogo, dependencias, tabla);
    }

    private ReglaHabilidad regla(String id, String counter, String confianza, boolean esUlti, String motivo) {
        return new ReglaHabilidad(id, List.of("Storm Spirit"), "escape", counter, "x_hex", "Hex",
                List.of("x_ultimate"), false, esUlti, confianza, motivo);
    }

    private void reglasParaStorm(RepositorioReglas.ReglaResuelta... reglas) {
        when(repositorio.reglasPara(6, datos)).thenReturn(List.of(reglas));
    }

    private ContextoDraft contexto(Set<Integer> rivalesDirectos, List<HeroeMeta> candidatos) {
        return new ContextoDraft(4, Map.of(), List.of(), null, null, rivalesDirectos, candidatos, datos,
                new ArrayList<>());
    }

    @Test
    void metadatosDeLaFila() {
        assertThat(analizador.id()).isEqualTo("habilidad");
        assertThat(analizador.titulo()).isEqualTo("Sugerencias por jugabilidad");
        assertThat(analizador.orden()).isEqualTo(2);
    }

    @Test
    void sinRivalesDirectosLaFilaSaleVacia() {
        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(), List.of(LION, SHADOW_SHAMAN)));

        assertThat(r).isEmpty();
    }

    @Test
    void unCandidatoQueEsCounterDeUnRivalDirectoSumaPuntos() {
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                regla("r1", "Lion", "alta", false, "Hex lo inmoviliza aunque use Ball Lightning."), 26));

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6), List.of(LION, SHADOW_SHAMAN)));

        assertThat(r).hasSize(1);
        assertThat(r.get(0).heroId()).isEqualTo(26);
        assertThat(r.get(0).puntaje()).isEqualTo(1.0);
        assertThat(r.get(0).confianza()).isEqualTo("alta");
        assertThat(r.get(0).motivo()).isEqualTo("Hex lo inmoviliza aunque use Ball Lightning.");
    }

    @Test
    void unCandidatoQueNoEsCounterDeNingunRivalDirectoNoApareceLaFilaQuedaVacia() {
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                regla("r1", "Lion", "alta", false, "motivo"), 26));

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6), List.of(SHADOW_SHAMAN)));

        assertThat(r).isEmpty();
    }

    @Test
    void unRivalEnOtroSlotNoSumaAunqueTengaReglas() {
        // Storm Spirit (6) NO es rival directo en este contexto; la regla no debe aplicarse.
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                regla("r1", "Lion", "alta", false, "motivo"), 26));

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(39), List.of(LION)));

        assertThat(r).isEmpty();
    }

    @Test
    void mediaYBajaSePrefijanComoPosibleYAltaNo() {
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                regla("r1", "Lion", "media", false, "Hex le quita Astral Step."), 26));

        String motivo = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0).motivo();

        assertThat(motivo).startsWith("Posible: ");
    }

    @Test
    void siLaHabilidadDelCounterEsUltiSeAclaraDesdeNivel6() {
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                regla("r1", "Lion", "alta", true, "Hex lo inmoviliza."), 26));

        String motivo = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0).motivo();

        assertThat(motivo).contains("(desde nivel 6)");
    }

    @Test
    void variasReglasDelMismoRivalSuman() {
        reglasParaStorm(
                new RepositorioReglas.ReglaResuelta(regla("r1", "Lion", "alta", false, "motivo alto"), 26),
                new RepositorioReglas.ReglaResuelta(regla("r2", "Lion", "media", false, "motivo medio"), 26));

        Sugerencia s = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0);

        assertThat(s.puntaje()).isEqualTo(1.5); // alta (1.0) + media (0.5)
        assertThat(s.confianza()).isEqualTo("alta"); // la más alta de las que coincidieron
    }

    @Test
    void elMotivoJuntaComoMaximoLasDosMejoresReglas() {
        when(repositorio.reglasPara(6, datos)).thenReturn(List.of(
                new RepositorioReglas.ReglaResuelta(regla("r1", "Lion", "alta", false, "m1"), 26)));
        when(repositorio.reglasPara(39, datos)).thenReturn(List.of(
                new RepositorioReglas.ReglaResuelta(regla("r2", "Lion", "alta", false, "m2"), 26)));

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6, 39), List.of(LION)));

        assertThat(r.get(0).motivo()).contains("m1").contains("m2");
    }

    @Test
    void variosRivalesDirectosConReglasDistintasSumanSusPesos() {
        when(repositorio.reglasPara(6, datos)).thenReturn(List.of(
                new RepositorioReglas.ReglaResuelta(regla("r1", "Lion", "alta", false, "m1"), 26)));
        when(repositorio.reglasPara(39, datos)).thenReturn(List.of(
                new RepositorioReglas.ReglaResuelta(regla("r2", "Lion", "media", false, "m2"), 26)));

        Sugerencia s = analizador.sugerir(contexto(Set.of(6, 39), List.of(LION))).get(0);

        assertThat(s.puntaje()).isEqualTo(1.5);
    }

    @Test
    void sinReglasQueCoincidanLaFilaSaleVaciaNoEsError() {
        reglasParaStorm();

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6), List.of(LION, SHADOW_SHAMAN)));

        assertThat(r).isEmpty();
    }

    @Test
    void ordenaPorPuntosDescendenteYDesempataPorElOrdenDeCandidatos() {
        // Ambos candidatos consiguen el mismo puntaje (alta = 1.0); ctx.candidatos() ya trae a Shadow Shaman
        // primero, así que debe ganar el desempate.
        when(repositorio.reglasPara(6, datos)).thenReturn(List.of(
                new RepositorioReglas.ReglaResuelta(regla("r1", "Lion", "alta", false, "m1"), 26)));
        when(repositorio.reglasPara(39, datos)).thenReturn(List.of(
                new RepositorioReglas.ReglaResuelta(regla("r2", "Shadow Shaman", "alta", false, "m2"), 27)));

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6, 39), List.of(SHADOW_SHAMAN, LION)));

        assertThat(r).extracting(Sugerencia::heroId).containsExactly(27, 26);
    }

    @Test
    void unCandidatoFueraDelPoolDeLaPosicionNoSeSugiereAunqueSeaCounter() {
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                regla("r1", "Lion", "alta", false, "motivo"), 26));

        // Lion no está en ctx.candidatos() (p. ej. ya elegido, o no candidato de esta posición)
        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6), List.of(OTRO)));

        assertThat(r).isEmpty();
    }

    // --- Interacciones automáticas (CatalogoHabilidades + TablaInteraccionesAutomaticas) ---

    private static final HeroeMeta MID_CANDIDATO = new HeroeMeta(50, "Mid Candidato", Set.of(), 1000, 500);

    /** Caso del reporte original: posición 2 contra un héroe tipo Storm Spirit (escape por habilidad activa,
     *  Point Target) en el slot 2, SIN ninguna regla escrita a mano, debe seguir dando una fila no vacía
     *  gracias a la interacción automática ESCAPE&lt;-HEX. */
    @Test
    void posicion2ContraUnEscapePorHabilidadDaUnaSugerenciaAutomaticaAunqueNoHayaReglaAMano() {
        reglasParaStorm(); // ninguna regla escrita a mano coincide
        when(catalogo.perfilDe(6, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(6,
                Map.of(Rasgo.ESCAPE, List.of(new HabilidadDetectada("storm_spirit_ball_lightning",
                        "Ball Lightning", true))),
                Map.of())));
        when(catalogo.perfilDe(50, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(50,
                Map.of(),
                Map.of(Capacidad.HEX, List.of(new HabilidadDetectada("x_hex", "Hex", false))))));

        List<Sugerencia> r = analizador.sugerir(
                new ContextoDraft(2, Map.of(2, 6), List.of(), null, null, Set.of(6), List.of(MID_CANDIDATO), datos,
                        new ArrayList<>()));

        assertThat(r).hasSize(1);
        assertThat(r.get(0).heroId()).isEqualTo(50);
        assertThat(r.get(0).confianza()).isEqualTo("media");
        assertThat(r.get(0).motivo()).isEqualTo("Posible: Hex anula Ball Lightning, su escape (ulti).");
    }

    @Test
    void siLaHabilidadDelCandidatoEsUltiLaInteraccionAutomaticaTambienLoAclara() {
        reglasParaStorm();
        when(catalogo.perfilDe(6, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(6,
                Map.of(Rasgo.ESCAPE, List.of(new HabilidadDetectada("storm_spirit_ball_lightning",
                        "Ball Lightning", false))),
                Map.of())));
        when(catalogo.perfilDe(26, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(26,
                Map.of(),
                Map.of(Capacidad.HEX, List.of(new HabilidadDetectada("x_hex", "Hex", true))))));

        String motivo = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0).motivo();

        assertThat(motivo).isEqualTo("Posible: Hex anula Ball Lightning, su escape. (desde nivel 6)");
    }

    @Test
    void trueSightRevelaInvisibilidadConElFormatoDelEjemploDelEncargo() {
        reglasParaStorm();
        when(catalogo.perfilDe(6, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(6,
                Map.of(Rasgo.INVISIBLE, List.of(new HabilidadDetectada("nyx_assassin_shadow_walk", "Shadow Walk", false))),
                Map.of())));
        when(catalogo.perfilDe(26, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(26,
                Map.of(),
                Map.of(Capacidad.TRUE_SIGHT, List.of(new HabilidadDetectada("zuus_lightning_bolt", "Lightning Bolt", false))))));

        String motivo = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0).motivo();

        assertThat(motivo).isEqualTo("Posible: Lightning Bolt revela su invisibilidad (Shadow Walk).");
    }

    @Test
    void unaCapacidadSinInteraccionConElRasgoDelRivalNoSumaPuntos() {
        reglasParaStorm();
        when(catalogo.perfilDe(6, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(6,
                Map.of(Rasgo.INVISIBLE, List.of(new HabilidadDetectada("x", "Shadow Walk", false))), Map.of())));
        when(catalogo.perfilDe(26, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(26,
                Map.of(), Map.of(Capacidad.HEX, List.of(new HabilidadDetectada("lion_voodoo", "Hex", false))))));

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6), List.of(LION)));

        assertThat(r).isEmpty();
    }

    @Test
    void unCandidatoConReglaAManoYConInteraccionAutomaticaSumaAmbasYElMotivoPriorizaLaDeMano() {
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                regla("r1", "Lion", "alta", false, "Hex lo inmoviliza aunque use Ball Lightning."), 26));
        when(catalogo.perfilDe(6, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(6,
                Map.of(Rasgo.ESCAPE, List.of(new HabilidadDetectada("storm_spirit_ball_lightning",
                        "Ball Lightning", false))),
                Map.of())));
        when(catalogo.perfilDe(26, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(26,
                Map.of(),
                Map.of(Capacidad.HEX, List.of(new HabilidadDetectada("lion_voodoo", "Hex", false))))));

        Sugerencia s = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0);

        assertThat(s.heroId()).isEqualTo(26);
        assertThat(s.confianza()).isEqualTo("alta"); // la de mano, que pesa más, gana el desempate
        assertThat(s.puntaje()).isEqualTo(1.25); // 1.0 (alta) + 0.5*0.5*1.0 (base automática * multiplicador)
        assertThat(s.motivo()).isEqualTo(
                "Hex lo inmoviliza aunque use Ball Lightning.; Posible: Hex anula Ball Lightning, su escape.");
    }

    @Test
    void siElCatalogoAutomaticoNoEstaDisponibleSeAñadeUnaAdvertenciaYSigueConLasReglasAMano() {
        when(catalogo.disponible(datos)).thenReturn(false);
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                regla("r1", "Lion", "alta", false, "motivo"), 26));
        ContextoDraft ctx = contexto(Set.of(6), List.of(LION));

        List<Sugerencia> r = analizador.sugerir(ctx);

        assertThat(r).hasSize(1); // la regla a mano sigue funcionando sin el catálogo automático
        assertThat(ctx.advertencias()).anyMatch(a -> a.toLowerCase().contains("habilidades"));
        org.mockito.Mockito.verify(catalogo, org.mockito.Mockito.never()).perfilDe(anyInt(), any());
    }

    // --- Tabla curada de dependencias (reemplaza a lo automático por palabra clave para esos enemigos) ---

    private void stormEsCurado() {
        when(dependencias.tieneDependencias(6, datos)).thenReturn(true);
    }

    private Contrarresto contrarresto(int candidatoId, String motivo, String confianza, boolean esUlti, double peso) {
        return new Contrarresto(6, candidatoId, Mecanismo.ESCAPE, Efecto.HEX, "lion_voodoo", motivo, confianza, esUlti, peso);
    }

    @Test
    void unEnemigoCuradoUsaLaTablaYNuncaLasInteraccionesAutomaticasPorPalabraClave() {
        stormEsCurado();
        reglasParaStorm();
        // El catálogo por palabra clave diría que Lion hexea el escape de Storm: para un enemigo curado se ignora.
        when(catalogo.perfilDe(6, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(6,
                Map.of(Rasgo.ESCAPE, List.of(new HabilidadDetectada("storm_spirit_ball_lightning", "Ball Lightning", true))),
                Map.of())));
        when(catalogo.perfilDe(26, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(26,
                Map.of(), Map.of(Capacidad.HEX, List.of(new HabilidadDetectada("lion_voodoo", "Hex", false))))));
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of());

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6), List.of(LION)));

        assertThat(r).isEmpty();
        org.mockito.Mockito.verify(catalogo, org.mockito.Mockito.never()).perfilDe(anyInt(), any());
    }

    @Test
    void unEnemigoCuradoSugiereLoQueDiceLaTablaConSuPesoYSuMotivo() {
        stormEsCurado();
        reglasParaStorm();
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of(
                contrarresto(26, "Storm Spirit depende de Ball Lightning (escape): Hex de Lion lo desactiva 2 s a nivel 1.",
                        "alta", false, 0.75)));

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6), List.of(LION, SHADOW_SHAMAN)));

        assertThat(r).hasSize(1);
        assertThat(r.get(0).heroId()).isEqualTo(26);
        assertThat(r.get(0).puntaje()).isEqualTo(0.75);
        assertThat(r.get(0).confianza()).isEqualTo("alta");
        assertThat(r.get(0).motivo()).isEqualTo(
                "Storm Spirit depende de Ball Lightning (escape): Hex de Lion lo desactiva 2 s a nivel 1.");
    }

    @Test
    void laTablaRespetaLasConvencionesDePosibleYDesdeNivel6() {
        stormEsCurado();
        reglasParaStorm();
        // El motivo curado ya trae "(desde nivel 6)" dentro de la oración: la fila no lo repite al final.
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of(
                contrarresto(26, "motivo curado (desde nivel 6).", "media", true, 0.2)));

        String motivo = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0).motivo();

        assertThat(motivo).isEqualTo("Posible: motivo curado (desde nivel 6).");
    }

    @Test
    void laTablaSoloCuentaContraRivalesDirectos() {
        stormEsCurado();
        reglasParaStorm();
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of(contrarresto(26, "m", "alta", false, 0.75)));

        // Storm (6) no es rival directo: solo lo es la Reina del Dolor (39), que no está curada.
        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(39), List.of(LION)));

        assertThat(r).isEmpty();
        org.mockito.Mockito.verify(tabla, org.mockito.Mockito.never()).contrarrestos(anyInt(), anyInt(), any());
    }

    @Test
    void lasReglasAManoSiguenAplicandoYSeSumanALaTablaParaUnEnemigoCurado() {
        stormEsCurado();
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                regla("r1", "Lion", "alta", false, "Hex lo inmoviliza aunque use Ball Lightning."), 26));
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of(contrarresto(26, "motivo curado.", "alta", false, 0.75)));

        Sugerencia s = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0);

        assertThat(s.puntaje()).isEqualTo(1.75);
        assertThat(s.motivo()).isEqualTo("Hex lo inmoviliza aunque use Ball Lightning.; motivo curado.");
    }

    @Test
    void unEnemigoSinDependenciasCuradasSigueConLasInteraccionesAutomaticas() {
        reglasParaStorm(); // Storm NO es curado en este test
        when(catalogo.perfilDe(6, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(6,
                Map.of(Rasgo.ESCAPE, List.of(new HabilidadDetectada("storm_spirit_ball_lightning", "Ball Lightning", false))),
                Map.of())));
        when(catalogo.perfilDe(26, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(26,
                Map.of(), Map.of(Capacidad.HEX, List.of(new HabilidadDetectada("lion_voodoo", "Hex", false))))));

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6), List.of(LION)));

        assertThat(r).hasSize(1);
        assertThat(r.get(0).motivo()).isEqualTo("Posible: Hex anula Ball Lightning, su escape.");
        org.mockito.Mockito.verify(tabla, org.mockito.Mockito.never()).contrarrestos(anyInt(), anyInt(), any());
    }

    @Test
    void siLaTablaNoEstaDisponibleSeAvisaYSigueConLasReglasAManoSinAutomaticoParaElCurado() {
        stormEsCurado();
        when(tabla.disponible(datos)).thenReturn(false);
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(regla("r1", "Lion", "alta", false, "motivo"), 26));
        ContextoDraft ctx = contexto(Set.of(6), List.of(LION));

        List<Sugerencia> r = analizador.sugerir(ctx);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).motivo()).isEqualTo("motivo");
        assertThat(ctx.advertencias()).anyMatch(a -> a.contains("dependencias") && a.contains("OpenDota"));
        org.mockito.Mockito.verify(tabla, org.mockito.Mockito.never()).contrarrestos(anyInt(), anyInt(), any());
    }

    @Test
    void siNingunRivalDirectoEsCuradoNoSeConstruyeLaTabla() {
        reglasParaStorm();

        analizador.sugerir(contexto(Set.of(6), List.of(LION)));

        org.mockito.Mockito.verify(tabla, org.mockito.Mockito.never()).disponible(any());
    }

    private ReglaHabilidad reglaDe(String id, String habilidad, String confianza, String motivo) {
        return new ReglaHabilidad(id, List.of("Storm Spirit"), "escape", "Lion", habilidad, "Hex",
                List.of("x_ultimate"), false, false, confianza, motivo);
    }

    @Test
    void siUnaReglaAManoYUnaHerramientaCuradaSonDeLaMismaHabilidadCuentaUnaSolaVezConElMayorPesoYElMotivoDeLaRegla() {
        stormEsCurado();
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                reglaDe("r1", "lion_voodoo", "media", "Hex de la regla a mano."), 26));
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of(contrarresto(26, "motivo curado.", "alta", false, 0.75)));

        Sugerencia s = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0);

        assertThat(s.puntaje()).isEqualTo(0.75); // el mayor (curado 0.75 > mano media 0.5), no la suma
        assertThat(s.motivo()).isEqualTo("Posible: Hex de la regla a mano."); // solo el texto de la regla a mano
    }

    @Test
    void siLaReglaAManoPesaMasQueLaCuradaDeLaMismaHabilidadGanaLaDeMano() {
        stormEsCurado();
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                reglaDe("r1", "lion_voodoo", "alta", "Hex de la regla a mano."), 26));
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of(contrarresto(26, "motivo curado.", "media", false, 0.3)));

        Sugerencia s = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0);

        assertThat(s.puntaje()).isEqualTo(1.0);
        assertThat(s.motivo()).isEqualTo("Hex de la regla a mano.");
    }

    @Test
    void habilidadesDistintasSiguenSumandoYaNoSeDeduplican() {
        stormEsCurado();
        reglasParaStorm(new RepositorioReglas.ReglaResuelta(
                reglaDe("r1", "lion_impale", "alta", "Otra habilidad a mano."), 26));
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of(contrarresto(26, "motivo curado.", "alta", false, 0.75)));

        Sugerencia s = analizador.sugerir(contexto(Set.of(6), List.of(LION))).get(0);

        assertThat(s.puntaje()).isEqualTo(1.75);
        assertThat(s.motivo()).isEqualTo("Otra habilidad a mano.; motivo curado.");
    }

    @Test
    void paraUnRivalCuradoNuncaSeConsultaElCatalogoPorPalabraClaveNiPorEseRivalNiPorElCandidato() {
        stormEsCurado();
        reglasParaStorm();
        when(catalogo.perfilDe(6, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(6,
                Map.of(Rasgo.ESCAPE, List.of(new HabilidadDetectada("storm_spirit_ball_lightning", "Ball Lightning", false))),
                Map.of())));
        when(catalogo.perfilDe(26, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(26,
                Map.of(), Map.of(Capacidad.HEX, List.of(new HabilidadDetectada("lion_voodoo", "Hex", false))))));

        analizador.sugerir(contexto(Set.of(6), List.of(LION, SHADOW_SHAMAN)));

        org.mockito.Mockito.verify(catalogo, org.mockito.Mockito.never()).perfilDe(anyInt(), any());
    }

    @Test
    void unRivalNoCuradoSigueConLoAutomaticoEnLaMismaPeticionEnQueOtroRivalEsCurado() {
        stormEsCurado(); // 6 curado; 39 (Reina del Dolor) no
        reglasParaStorm();
        when(repositorio.reglasPara(39, datos)).thenReturn(List.of());
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of(contrarresto(26, "motivo curado.", "alta", false, 0.75)));
        when(catalogo.perfilDe(39, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(39,
                Map.of(Rasgo.ESCAPE, List.of(new HabilidadDetectada("queenofpain_blink", "Blink", false))), Map.of())));
        when(catalogo.perfilDe(27, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(27,
                Map.of(), Map.of(Capacidad.HEX, List.of(new HabilidadDetectada("shadow_shaman_voodoo", "Hex", false))))));

        List<Sugerencia> r = analizador.sugerir(contexto(Set.of(6, 39), List.of(LION, SHADOW_SHAMAN)));

        assertThat(r).extracting(Sugerencia::heroId).containsExactly(26, 27);
        assertThat(r.get(0).motivo()).isEqualTo("motivo curado.");
        assertThat(r.get(1).motivo()).isEqualTo("Posible: Hex anula Blink, su escape.");
        org.mockito.Mockito.verify(catalogo, org.mockito.Mockito.never())
                .perfilDe(org.mockito.ArgumentMatchers.eq(6), any());
    }

    @Test
    void laAdvertenciaDelCatalogoAutomaticoSoloSaleSiAlgunRivalNecesitabaLoAutomatico() {
        stormEsCurado();
        reglasParaStorm();
        when(catalogo.disponible(any())).thenReturn(false);
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of());
        ContextoDraft soloCurado = contexto(Set.of(6), List.of(LION));

        analizador.sugerir(soloCurado);

        assertThat(soloCurado.advertencias()).isEmpty();
    }

    @Test
    void laAdvertenciaDelCatalogoAutomaticoSaleSiHayUnRivalNoCuradoAunqueOtroSeaCurado() {
        stormEsCurado();
        reglasParaStorm();
        when(repositorio.reglasPara(39, datos)).thenReturn(List.of());
        when(catalogo.disponible(any())).thenReturn(false);
        ContextoDraft ctx = contexto(Set.of(6, 39), List.of(LION));

        analizador.sugerir(ctx);

        assertThat(ctx.advertencias()).hasSize(1);
        assertThat(ctx.advertencias().get(0)).contains("interacciones automáticas");
    }
}
