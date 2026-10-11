package com.dotastats.analizadores.habilidad;

import com.dotastats.analizadores.ContextoDraft;
import com.dotastats.analizadores.habilidad.dependencias.MuestraOpenDota;
import com.dotastats.analizadores.habilidad.dependencias.RepositorioDependencias;
import com.dotastats.analizadores.habilidad.dependencias.TablaContrarrestos;
import com.dotastats.draft.Sugerencia;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.HeroeMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * La fila de jugabilidad de punta a punta con el archivo curado REAL ({@code dependencias-heroes.json}) y un
 * subconjunto real de las constantes de OpenDota ({@link MuestraOpenDota}); solo el servicio de meta es un mock.
 */
class AnalizadorHabilidadDependenciasTest {

    private MuestraOpenDota muestra;
    private DatosMeta datos;
    private DatosMetaService metaService;
    private AnalizadorHabilidad analizador;

    @BeforeEach
    void preparar() {
        muestra = MuestraOpenDota.cargar();
        datos = muestra.datosMeta();
        metaService = mock(DatosMetaService.class);
        when(metaService.datosHabilidad()).thenReturn(muestra.datosHabilidad());

        HabilidadProperties props = new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null, null);
        RepositorioReglas reglas = mock(RepositorioReglas.class); // sin reglas a mano: solo se prueba lo curado
        CatalogoHabilidades catalogo = mock(CatalogoHabilidades.class);
        when(catalogo.disponible(any())).thenReturn(true);
        when(catalogo.perfilDe(anyInt(), any())).thenReturn(Optional.empty());
        RepositorioDependencias dependencias = new RepositorioDependencias(new DefaultResourceLoader(), props);
        dependencias.cargar();
        analizador = new AnalizadorHabilidad(reglas, props, catalogo, dependencias,
                new TablaContrarrestos(metaService, dependencias, props));
    }

    private HeroeMeta heroe(int id) {
        return datos.heroes().get(id);
    }

    private ContextoDraft contexto(int miRol, int slot, int enemigoId, int... candidatos) {
        List<HeroeMeta> lista = new ArrayList<>();
        for (int id : candidatos) {
            lista.add(heroe(id));
        }
        return new ContextoDraft(miRol, Map.of(slot, enemigoId), List.of(), null, null, Set.of(enemigoId), lista, datos,
                new ArrayList<>());
    }

    @Test
    void posicion2ContraStormSpiritNoSugiereAHuskarPorSilencioYSiAQuienTieneUnaHerramientaReal() {
        ContextoDraft ctx = contexto(2, 2, MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.HUSKAR, MuestraOpenDota.LION,
                27 /* Shadow Shaman */, MuestraOpenDota.DEATH_PROPHET, MuestraOpenDota.ANTI_MAGE);

        List<Sugerencia> r = analizador.sugerir(ctx);

        assertThat(r).extracting(Sugerencia::heroId).doesNotContain(MuestraOpenDota.HUSKAR);
        assertThat(r).extracting(Sugerencia::motivo).noneMatch(m -> m.contains("Inner Fire"));
        assertThat(r.get(0).heroId()).isEqualTo(MuestraOpenDota.LION);
        assertThat(r.get(0).confianza()).isEqualTo("alta");
        assertThat(r.get(0).motivo()).startsWith("Storm Spirit depende de Ball Lightning (escape): Hex de Lion "
                + "lo desactiva 2 s en su primer nivel, lanzamiento instantáneo.");
        // Hex (Lion, Shadow Shaman) > quemar maná (Anti-Mage, Mana Break) > silencio (Death Prophet).
        assertThat(r).extracting(Sugerencia::heroId).containsExactly(MuestraOpenDota.LION, 27,
                MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.DEATH_PROPHET);
        assertThat(r.get(2).motivo()).startsWith("Posible: Storm Spirit depende de Ball Lightning (maná)");
        assertThat(r.get(3).motivo()).contains("Death Prophet");
    }

    @Test
    void contraRikiUnaHerramientaDeVisionVerdaderaVaPrimeroYUnHexNoCuenta() {
        ContextoDraft ctx = contexto(2, 2, MuestraOpenDota.RIKI, MuestraOpenDota.ZEUS, MuestraOpenDota.DROW,
                MuestraOpenDota.SLARDAR, MuestraOpenDota.LESHRAC, MuestraOpenDota.LION);

        List<Sugerencia> r = analizador.sugerir(ctx);

        assertThat(r).extracting(Sugerencia::heroId).containsExactly(MuestraOpenDota.ZEUS, MuestraOpenDota.DROW,
                MuestraOpenDota.SLARDAR, MuestraOpenDota.LESHRAC);
        assertThat(r.get(0).motivo()).contains("Lightning Bolt de Zeus revela su invisibilidad");
        // Slardar: su revelador es una ultimate y de confianza media.
        Sugerencia slardar = r.get(2);
        assertThat(slardar.motivo()).startsWith("Posible: Riki depende de Cloak and Dagger (invisibilidad)")
                .endsWith("(desde nivel 6).")
                .doesNotContain("a nivel 1");
    }

    @Test
    void contraViperElRangoMayorSaleDelAlcanceDeAtaqueDeLasEstadisticas() {
        ContextoDraft ctx = contexto(3, 1, MuestraOpenDota.VIPER, MuestraOpenDota.LION, MuestraOpenDota.DROW,
                MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.STORM_SPIRIT);

        List<Sugerencia> r = analizador.sugerir(ctx);

        // Drow (625) le saca 50 a Viper (575); Lion (600) solo 25, menos que el mínimo de 50; Anti-Mage y Storm no.
        assertThat(r).extracting(Sugerencia::heroId).containsExactly(MuestraOpenDota.DROW);
        assertThat(r.get(0).motivo()).isEqualTo("Posible: Viper depende de Poison Attack (acoso a distancia): "
                + "Drow Ranger tiene más alcance de ataque (625 frente a 575).");
        assertThat(r.get(0).confianza()).isEqualTo("media");
    }

    @Test
    void contraHuskarElBreakDeViperStrikeEsPosibleYDesdeNivel6() {
        ContextoDraft ctx = contexto(3, 1, MuestraOpenDota.HUSKAR, MuestraOpenDota.VIPER, MuestraOpenDota.LION);

        List<Sugerencia> r = analizador.sugerir(ctx);

        assertThat(r).extracting(Sugerencia::heroId).containsExactly(MuestraOpenDota.VIPER);
        assertThat(r.get(0).motivo()).startsWith("Posible: Huskar depende de Berserker's Blood (pasivas): Viper Strike de Viper")
                .endsWith("atraviesa la inmunidad a la magia (desde nivel 6).");
    }

    @Test
    void unEnemigoSinDependenciasCuradasNoTocaLaTabla() {
        // Lion no tiene dependencias curadas; el catálogo por palabra clave (aquí vacío) sería lo único que aplica.
        ContextoDraft ctx = contexto(3, 1, MuestraOpenDota.LION, MuestraOpenDota.DROW, MuestraOpenDota.ZEUS);

        assertThat(analizador.sugerir(ctx)).isEmpty();
        verifyNoMoreInteractions(metaService);
    }

    @Test
    void laTablaSeConstruyeUnaVezYLasSugerenciasSiguientesNoTocanOpenDota() {
        ContextoDraft ctx = contexto(2, 2, MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, MuestraOpenDota.DROW);

        for (int i = 0; i < 20; i++) {
            analizador.sugerir(ctx);
        }

        verify(metaService, times(1)).datosHabilidad();
        verifyNoMoreInteractions(metaService);
    }
}
