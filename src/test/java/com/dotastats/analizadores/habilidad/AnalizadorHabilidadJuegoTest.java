package com.dotastats.analizadores.habilidad;

import com.dotastats.analizadores.ContextoDraft;
import com.dotastats.analizadores.habilidad.dependencias.MuestraOpenDota;
import com.dotastats.analizadores.habilidad.dependencias.RepositorioDependencias;
import com.dotastats.analizadores.habilidad.dependencias.TablaContrarrestos;
import com.dotastats.draft.Sugerencia;
import com.dotastats.juego.IndicadoresHabilidad;
import com.dotastats.meta.DatosHabilidad;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.HeroeMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fila de jugabilidad con la SEGUNDA FUENTE (archivos del juego, AGENTS.md 5.3): un enemigo con dependencias
 * curadas O derivadas usa la tabla y NO el detector por palabra clave, que queda solo como respaldo cuando no hay
 * indicadores para ese enemigo.
 */
class AnalizadorHabilidadJuegoTest {

    private static final IndicadoresHabilidad ESCAPE = new IndicadoresHabilidad(true, false, false, false, false, false, false);
    private static final IndicadoresHabilidad DISABLE_DURO = new IndicadoresHabilidad(false, true, false, false, false, false, false);

    private MuestraOpenDota muestra;
    private DatosMeta datos;
    private DatosMetaService metaService;
    private RepositorioReglas reglas;
    private CatalogoHabilidades catalogo;
    private Map<String, Map<String, IndicadoresHabilidad>> indicadores;

    @BeforeEach
    void preparar() {
        muestra = MuestraOpenDota.cargar();
        datos = muestra.datosMeta();
        metaService = mock(DatosMetaService.class);
        reglas = mock(RepositorioReglas.class);
        catalogo = mock(CatalogoHabilidades.class);
        when(catalogo.disponible(any())).thenReturn(true);
        when(catalogo.perfilDe(anyInt(), any())).thenReturn(Optional.empty());
        indicadores = new HashMap<>();
        indicadores.put("npc_dota_hero_antimage", Map.of("antimage_blink", ESCAPE));
        indicadores.put("npc_dota_hero_lion", Map.of("lion_voodoo", DISABLE_DURO, "lion_impale", DISABLE_DURO));
        indicadores.put("npc_dota_hero_huskar", Map.of("huskar_inner_fire", DISABLE_DURO));
    }

    private AnalizadorHabilidad analizador() {
        DatosHabilidad base = muestra.datosHabilidad();
        when(metaService.datosHabilidad()).thenReturn(new DatosHabilidad(base.abilidades(), base.habilidadesPorHeroe(),
                base.rangoAtaquePorHeroe(), indicadores));
        HabilidadProperties props = new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null, null);
        RepositorioDependencias dependencias = new RepositorioDependencias(new DefaultResourceLoader(), props);
        dependencias.cargar();
        return new AnalizadorHabilidad(reglas, props, catalogo, dependencias,
                new TablaContrarrestos(metaService, dependencias, props));
    }

    private ContextoDraft contexto(int enemigoId, int... candidatos) {
        List<HeroeMeta> lista = new ArrayList<>();
        for (int id : candidatos) {
            lista.add(datos.heroes().get(id));
        }
        return new ContextoDraft(2, Map.of(2, enemigoId), List.of(), null, null, Set.of(enemigoId), lista, datos,
                new ArrayList<>());
    }

    @Test
    void unEnemigoConDependenciasDerivadasUsaLaTablaYNuncaElDetectorPorPalabraClave() {
        ContextoDraft ctx = contexto(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION, MuestraOpenDota.HUSKAR);

        List<Sugerencia> r = analizador().sugerir(ctx);

        assertThat(r).extracting(Sugerencia::heroId).containsExactly(MuestraOpenDota.LION, MuestraOpenDota.HUSKAR);
        assertThat(r.get(0).motivo()).startsWith("Posible: Anti-Mage depende de Blink (escape): Hex de Lion lo desactiva "
                + "2 s en su primer nivel, lanzamiento instantáneo.");
        assertThat(r.get(0).confianza()).isEqualTo("baja");
        assertThat(r.get(1).motivo()).isEqualTo("Posible: Anti-Mage depende de Blink (escape): Inner Fire de Huskar lo "
                + "desactiva (disable duro), lanzamiento de 0,35 s.");
        verify(catalogo, never()).disponible(any());
        verify(catalogo, never()).perfilDe(anyInt(), any());
        assertThat(ctx.advertencias()).isEmpty();
    }

    @Test
    void unEnemigoSinIndicadoresDelJuegoVuelveAlDetectorPorPalabraClave() {
        ContextoDraft ctx = contexto(MuestraOpenDota.ZEUS, MuestraOpenDota.LION);

        List<Sugerencia> r = analizador().sugerir(ctx);

        assertThat(r).isEmpty(); // el catálogo simulado no detecta nada
        verify(catalogo, atLeastOnce()).disponible(any());
        verify(catalogo, atLeastOnce()).perfilDe(anyInt(), any());
        // Hay indicadores del juego (de otros héroes): no es una caída de la fuente, así que no hay advertencia.
        assertThat(ctx.advertencias()).isEmpty();
    }

    @Test
    void siLosArchivosDelJuegoNoEstanDisponiblesTodoSigueComoAntesYSeAvisa() {
        indicadores = new HashMap<>();
        ContextoDraft ctx = contexto(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION);

        List<Sugerencia> r = analizador().sugerir(ctx);

        assertThat(r).isEmpty();
        verify(catalogo, atLeastOnce()).perfilDe(anyInt(), any());
        assertThat(ctx.advertencias()).singleElement().asString().contains("archivos del juego");
    }

    @Test
    void sinArchivosDelJuegoLosEnemigosCuradosSiguenFuncionandoComoAntes() {
        indicadores = new HashMap<>();
        ContextoDraft ctx = contexto(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION);

        List<Sugerencia> r = analizador().sugerir(ctx);

        assertThat(r).extracting(Sugerencia::heroId).containsExactly(MuestraOpenDota.LION);
        assertThat(r.get(0).confianza()).isEqualTo("alta");
        verify(catalogo, never()).perfilDe(anyInt(), any());
        assertThat(ctx.advertencias()).isEmpty();
    }

    @Test
    void unEnemigoConIndicadoresPeroSinDependenciaDerivadaConservaLaDeteccionPorPalabraClave() {
        // Lion no tiene blinks: de sus indicadores no sale ninguna dependencia, así que la tabla no lo cubre y su fila
        // sigue siendo la de 5.1 (si no, los héroes invisibles o de ilusiones se quedarían sin nada).
        ContextoDraft ctx = contexto(MuestraOpenDota.LION, MuestraOpenDota.HUSKAR);

        assertThat(analizador().sugerir(ctx)).isEmpty(); // el catálogo simulado no detecta nada
        verify(catalogo, atLeastOnce()).disponible(any());
        verify(catalogo, atLeastOnce()).perfilDe(anyInt(), any());
    }

    @Test
    void laReglaAManoYLaHerramientaCuradaDeLaMismaHabilidadSiguenContandoUnaSolaVez() {
        ReglaHabilidad regla = new ReglaHabilidad("r1", List.of("Anti-Mage"), "escape", "Lion", "lion_voodoo", "Hex",
                List.of("antimage_blink"), false, false, "alta", "Motivo escrito a mano.");
        when(reglas.reglasPara(MuestraOpenDota.ANTI_MAGE, datos))
                .thenReturn(List.of(new RepositorioReglas.ReglaResuelta(regla, MuestraOpenDota.LION)));
        ContextoDraft ctx = contexto(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION);

        List<Sugerencia> r = analizador().sugerir(ctx);

        assertThat(r).singleElement().satisfies(s -> {
            // El mayor de los dos pesos (la regla a mano, 1.0), no la suma con el camino derivado (0.1875).
            assertThat(s.puntaje()).isCloseTo(1.0, within(1e-9));
            assertThat(s.motivo()).startsWith("Motivo escrito a mano.");
            assertThat(s.confianza()).isEqualTo("alta");
        });
    }
}
