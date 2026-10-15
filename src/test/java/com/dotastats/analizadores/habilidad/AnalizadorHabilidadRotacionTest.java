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

/** Rotación de soportes: el mid enemigo también cuenta (con menos peso) para las posiciones 4 y 5. */
class AnalizadorHabilidadRotacionTest {

    private static final HeroeMeta LION = new HeroeMeta(26, "Lion", Set.of(), 1000, 500);
    private static final HeroeMeta STORM_SPIRIT = new HeroeMeta(6, "Storm Spirit", Set.of(), 1000, 500);
    private static final HeroeMeta QUEEN_OF_PAIN = new HeroeMeta(39, "Queen of Pain", Set.of(), 1000, 500);

    private RepositorioReglas repositorio;
    private CatalogoHabilidades catalogo;
    private RepositorioDependencias dependencias;
    private TablaContrarrestos tabla;
    private DatosMeta datos;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioReglas.class);
        catalogo = mock(CatalogoHabilidades.class);
        dependencias = mock(RepositorioDependencias.class);
        tabla = mock(TablaContrarrestos.class);
        when(tabla.disponible(any())).thenReturn(true);
        when(catalogo.disponible(any())).thenReturn(true);
        when(catalogo.perfilDe(anyInt(), any())).thenReturn(Optional.empty());
        datos = new DatosMeta(Map.of(26, LION, 6, STORM_SPIRIT, 39, QUEEN_OF_PAIN),
                new Parche(60, "7.41"), Instant.parse("2026-10-10T12:00:00Z"));
    }

    private AnalizadorHabilidad analizador(RotacionProperties rotacion) {
        return new AnalizadorHabilidad(repositorio,
                new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null, null),
                rotacion, catalogo, dependencias, tabla);
    }

    private void reglaDeLionContraStorm(String confianza) {
        ReglaHabilidad regla = new ReglaHabilidad("r1", List.of("Storm Spirit"), "escape", "Lion", "x_hex", "Hex",
                List.of("x_ultimate"), false, false, confianza, "Hex lo inmoviliza aunque use Ball Lightning.");
        when(repositorio.reglasPara(6, datos)).thenReturn(List.of(new RepositorioReglas.ReglaResuelta(regla, 26)));
    }

    private ContextoDraft contexto(int miRol, Map<Integer, Integer> enemigos, Set<Integer> directos) {
        return new ContextoDraft(miRol, enemigos, List.of(), null, null, directos, List.of(LION), datos,
                new ArrayList<>());
    }

    @Test
    void elSoporte4ContraStormEnElMidRecibeALionPorRotacion() {
        reglaDeLionContraStorm("alta");

        List<Sugerencia> r = analizador(new RotacionProperties(null, 0.5))
                .sugerir(contexto(4, Map.of(2, 6), Set.of()));

        assertThat(r).hasSize(1);
        assertThat(r.get(0).heroId()).isEqualTo(26);
        assertThat(r.get(0).puntaje()).isEqualTo(0.5);
        assertThat(r.get(0).confianza()).isEqualTo("alta");
        assertThat(r.get(0).motivo()).isEqualTo("Al rotar a mid: Hex lo inmoviliza aunque use Ball Lightning.");
    }

    @Test
    void elSoporte5TambienRota() {
        reglaDeLionContraStorm("alta");

        List<Sugerencia> r = analizador(new RotacionProperties(null, 0.5))
                .sugerir(contexto(5, Map.of(2, 6), Set.of()));

        assertThat(r).extracting(Sugerencia::heroId).containsExactly(26);
    }

    @Test
    void elPesoDeRotacionConfigurableMultiplicaLosPuntos() {
        reglaDeLionContraStorm("alta");

        List<Sugerencia> r = analizador(new RotacionProperties(null, 0.8))
                .sugerir(contexto(4, Map.of(2, 6), Set.of()));

        assertThat(r.get(0).puntaje()).isEqualTo(0.8);
    }

    @Test
    void pesoCeroApagaLaRotacion() {
        reglaDeLionContraStorm("alta");

        List<Sugerencia> r = analizador(new RotacionProperties(null, 0.0))
                .sugerir(contexto(4, Map.of(2, 6), Set.of()));

        assertThat(r).isEmpty();
    }

    @Test
    void confianzaMediaSeAntepoeComoPosibleAlRotar() {
        reglaDeLionContraStorm("media");

        Sugerencia s = analizador(new RotacionProperties(null, 0.5))
                .sugerir(contexto(4, Map.of(2, 6), Set.of())).get(0);

        assertThat(s.motivo()).isEqualTo("Posible, al rotar a mid: Hex lo inmoviliza aunque use Ball Lightning.");
        assertThat(s.puntaje()).isEqualTo(0.25);
    }

    @Test
    void lasPosicionesDeNucleoNoRotan() {
        reglaDeLionContraStorm("alta");

        for (int posicion : new int[]{1, 2, 3}) {
            List<Sugerencia> r = analizador(new RotacionProperties(null, 0.5))
                    .sugerir(contexto(posicion, Map.of(2, 6), Set.of()));
            assertThat(r).as("posicion " + posicion).isEmpty();
        }
    }

    @Test
    void unRivalDirectoConservaPesoCompletoYSinPrefijo() {
        reglaDeLionContraStorm("alta");

        Sugerencia s = analizador(new RotacionProperties(null, 0.5))
                .sugerir(contexto(4, Map.of(1, 6), Set.of(6))).get(0);

        assertThat(s.puntaje()).isEqualTo(1.0);
        assertThat(s.motivo()).isEqualTo("Hex lo inmoviliza aunque use Ball Lightning.");
    }

    @Test
    void siUnEnemigoEsDirectoYDeRotacionSeUsaElPesoDirectoUnaSolaVez() {
        reglaDeLionContraStorm("alta");
        RotacionProperties rotacion = new RotacionProperties(Map.of(4, List.of(1)), 0.5);

        Sugerencia s = analizador(rotacion).sugerir(contexto(4, Map.of(1, 6), Set.of(6))).get(0);

        assertThat(s.puntaje()).isEqualTo(1.0);
        assertThat(s.motivo()).doesNotContain("rotar");
    }

    @Test
    void unSlotDeRotacionDistintoDeMidSeNombraPorSuNumero() {
        reglaDeLionContraStorm("alta");
        RotacionProperties rotacion = new RotacionProperties(Map.of(4, List.of(3)), 0.5);

        Sugerencia s = analizador(rotacion).sugerir(contexto(4, Map.of(3, 6), Set.of())).get(0);

        assertThat(s.motivo()).startsWith("Al rotar al slot 3: ");
    }

    @Test
    void lasDependenciasCuradasTambienRespetanLaRotacion() {
        when(dependencias.tieneDependencias(6, datos)).thenReturn(true);
        when(tabla.contrarrestos(6, 26, datos)).thenReturn(List.of(new Contrarresto(6, 26, Mecanismo.ESCAPE,
                Efecto.HEX, "x_hex", "Hex lo anula mientras se reposiciona.", "alta", false, 0.8)));
        when(repositorio.reglasPara(6, datos)).thenReturn(List.of());

        Sugerencia s = analizador(new RotacionProperties(null, 0.5))
                .sugerir(contexto(4, Map.of(2, 6), Set.of())).get(0);

        assertThat(s.puntaje()).isEqualTo(0.4);
        assertThat(s.motivo()).isEqualTo("Al rotar a mid: Hex lo anula mientras se reposiciona.");
    }

    @Test
    void lasInteraccionesAutomaticasTambienRespetanLaRotacion() {
        when(repositorio.reglasPara(6, datos)).thenReturn(List.of());
        when(catalogo.perfilDe(6, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(6,
                Map.of(Rasgo.ESCAPE, List.of(new HabilidadDetectada("storm_spirit_ball_lightning",
                        "Ball Lightning", true))),
                Map.of())));
        when(catalogo.perfilDe(26, datos)).thenReturn(Optional.of(new PerfilHabilidadesHeroe(26,
                Map.of(), Map.of(Capacidad.HEX, List.of(new HabilidadDetectada("x_hex", "Hex", false))))));
        AnalizadorHabilidad a = analizador(new RotacionProperties(null, 0.5));

        double directo = a.sugerir(contexto(2, Map.of(2, 6), Set.of(6))).get(0).puntaje();
        Sugerencia rotado = a.sugerir(contexto(4, Map.of(2, 6), Set.of())).get(0);

        assertThat(rotado.puntaje()).isEqualTo(directo * 0.5);
        assertThat(rotado.motivo()).isEqualTo("Posible, al rotar a mid: Hex anula Ball Lightning, su escape (ulti).");
    }
}
