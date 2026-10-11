package com.dotastats.analizadores.habilidad.dependencias;

import com.dotastats.analizadores.habilidad.HabilidadProperties;
import com.dotastats.analizadores.habilidad.JuegoProperties;
import com.dotastats.juego.IndicadoresHabilidad;
import com.dotastats.meta.DatosHabilidad;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dependencias y herramientas DERIVADAS de los archivos del juego (segunda fuente, ver AGENTS.md 5.3), contra un
 * subconjunto real de las constantes de OpenDota ({@link MuestraOpenDota}) y indicadores armados a mano.
 */
class TablaContrarrestosDerivadasTest {

    private static final int SVEN = 18;
    private static final int PUCK = 13;
    private static final int NAGA = 89;
    private static final String SIN_CURADOS = "{\"dependencias\":[],\"herramientas\":[]}";

    private static IndicadoresHabilidad ind(boolean root, boolean duro, boolean rompible, boolean pasiva, boolean oculta) {
        return new IndicadoresHabilidad(root, duro, rompible, pasiva, false, oculta, false);
    }

    private static final IndicadoresHabilidad ESCAPE = ind(true, false, false, false, false);
    private static final IndicadoresHabilidad DISABLE_DURO = ind(false, true, false, false, false);
    private static final IndicadoresHabilidad PASIVA_ROMPIBLE = ind(false, false, true, true, false);

    @TempDir
    Path tmp;

    private MuestraOpenDota muestra;
    private DatosMeta datos;
    private DatosMetaService metaService;
    private HabilidadProperties props;
    private Map<String, Map<String, IndicadoresHabilidad>> indicadores;

    @BeforeEach
    void preparar() {
        muestra = MuestraOpenDota.cargar();
        datos = muestra.datosMeta();
        metaService = mock(DatosMetaService.class);
        props = new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null, null);
        indicadores = new HashMap<>();
        indicadores.put("npc_dota_hero_antimage", Map.of("antimage_blink", ESCAPE, "antimage_mana_break", PASIVA_ROMPIBLE));
        indicadores.put("npc_dota_hero_sven", Map.of("sven_storm_bolt", DISABLE_DURO,
                // No es de Sven según hero_abilities: no puede ser su herramienta aunque venga en sus indicadores.
                "lion_voodoo", DISABLE_DURO));
        indicadores.put("npc_dota_hero_lion", Map.of("lion_voodoo", DISABLE_DURO, "lion_impale", DISABLE_DURO));
        indicadores.put("npc_dota_hero_puck", Map.of("puck_ethereal_jaunt", ESCAPE));
        indicadores.put("npc_dota_hero_naga_siren", Map.of("naga_siren_eelskin", PASIVA_ROMPIBLE));
        indicadores.put("npc_dota_hero_huskar", Map.of("huskar_inner_fire", ind(true, true, false, false, false), "huskar_berserkers_blood", PASIVA_ROMPIBLE));
        indicadores.put("npc_dota_hero_viper", Map.of("viper_poison_attack", ind(false, false, false, false, false)));
    }

    private TablaContrarrestos tabla(String json) throws IOException {
        Path archivo = tmp.resolve("dependencias.json");
        Files.writeString(archivo, json, StandardCharsets.UTF_8);
        HabilidadProperties propias = new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null,
                new HabilidadProperties.Dependencias("file:" + archivo.toAbsolutePath(), 50));
        RepositorioDependencias repo = new RepositorioDependencias(new DefaultResourceLoader(), propias);
        repo.cargar();
        DatosHabilidad base = muestra.datosHabilidad();
        when(metaService.datosHabilidad()).thenReturn(new DatosHabilidad(base.abilidades(), base.habilidadesPorHeroe(),
                base.rangoAtaquePorHeroe(), indicadores));
        return new TablaContrarrestos(metaService, repo, propias);
    }

    /** Con el archivo curado real que trae el jar. */
    private TablaContrarrestos tablaReal() throws IOException {
        RepositorioDependencias repo = new RepositorioDependencias(new DefaultResourceLoader(), props);
        repo.cargar();
        DatosHabilidad base = muestra.datosHabilidad();
        when(metaService.datosHabilidad()).thenReturn(new DatosHabilidad(base.abilidades(), base.habilidadesPorHeroe(),
                base.rangoAtaquePorHeroe(), indicadores));
        return new TablaContrarrestos(metaService, repo, props);
    }

    /** Igual que tablaReal() pero con las pasivas derivadas encendidas (apagadas por defecto). */
    private TablaContrarrestos tablaConPasivas() throws IOException {
        RepositorioDependencias repo = new RepositorioDependencias(new DefaultResourceLoader(), props);
        repo.cargar();
        publicarIndicadores(0L);
        return new TablaContrarrestos(metaService, repo, props, new JuegoProperties(true));
    }

    /** Lo que devolvería el servicio de meta con los indicadores actuales y esa versión de los indicadores. */
    private void publicarIndicadores(long version) {
        DatosHabilidad base = muestra.datosHabilidad();
        when(metaService.datosHabilidad()).thenReturn(new DatosHabilidad(base.abilidades(), base.habilidadesPorHeroe(),
                base.rangoAtaquePorHeroe(), indicadores));
        when(metaService.versionIndicadoresJuego()).thenReturn(version);
    }

    private static Contrarresto de(List<Contrarresto> lista, Mecanismo mecanismo) {
        return lista.stream().filter(c -> c.mecanismo() == mecanismo).findFirst().orElseThrow();
    }

    /** Lion solo con Earth Spike (disable duro): nada curado le gana, así que su herramienta es la derivada. */
    private void lionSoloConEarthSpike() {
        indicadores.put("npc_dota_hero_lion", Map.of("lion_impale", DISABLE_DURO));
    }

    // --- Dependencia ESCAPE derivada (enemigo sin dependencias curadas) ---

    @Test
    void elBlinkDeUnEnemigoSinDependenciasCuradasSeDerivaComoEscapeConHerramientaDerivadaSinInventarNumeros() throws IOException {
        lionSoloConEarthSpike();

        List<Contrarresto> lion = tabla(SIN_CURADOS).contrarrestos(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION, datos);

        Contrarresto escape = de(lion, Mecanismo.ESCAPE);
        assertThat(escape.motivo()).isEqualTo("Anti-Mage depende de Blink (escape): Earth Spike de Lion lo desactiva "
                + "(disable duro), lanzamiento de 0,3 s.");
        assertThat(escape.efecto()).isEqualTo(Efecto.STUN);
        assertThat(escape.habilidad()).isEqualTo("lion_impale");
        assertThat(escape.esUlti()).isFalse();
        // Los números del motivo salen de OpenDota (lanzamiento); nada de duración inventada.
        assertThat(escape.motivo()).doesNotContainPattern("\\d+ s en su primer nivel");
    }

    @Test
    void laConfianzaDeUnCaminoDerivadoEsLaMenorEntreLaDependenciaYLaHerramienta() throws IOException {
        TablaContrarrestos real = tablaReal();
        lionSoloConEarthSpike();
        TablaContrarrestos sinCurados = tabla(SIN_CURADOS);

        // Dependencia derivada (baja) x herramienta derivada (media) -> baja
        Contrarresto derivadaDerivada = de(sinCurados.contrarrestos(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION, datos), Mecanismo.ESCAPE);
        // Dependencia derivada (baja) x herramienta curada (Hex de Lion, alta) -> baja
        Contrarresto derivadaCurada = de(real.contrarrestos(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION, datos), Mecanismo.ESCAPE);
        // Dependencia curada (Storm Spirit) x herramienta derivada (Inner Fire de Huskar, media) -> media
        Contrarresto curadaDerivada = de(real.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.HUSKAR, datos), Mecanismo.ESCAPE);
        // Dependencia curada x herramienta curada (alta): sigue como siempre
        Contrarresto curadaCurada = de(real.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos), Mecanismo.ESCAPE);

        assertThat(derivadaDerivada.confianza()).isEqualTo("baja");
        assertThat(derivadaCurada.confianza()).isEqualTo("baja");
        assertThat(curadaDerivada.confianza()).isEqualTo("media");
        assertThat(curadaCurada.confianza()).isEqualTo("alta");
    }

    @Test
    void elPesoDeUnCaminoDerivadoUsaLaConfianzaMenor() throws IOException {
        TablaContrarrestos real = tablaReal();
        lionSoloConEarthSpike();
        TablaContrarrestos sinCurados = tabla(SIN_CURADOS);

        Contrarresto derivado = de(sinCurados.contrarrestos(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION, datos), Mecanismo.ESCAPE);
        Contrarresto hex = de(real.contrarrestos(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION, datos), Mecanismo.ESCAPE);

        // peso("baja") 0.25 x STUN/ESCAPE 0.6 x fiabilidad(sin duración, lanzamiento 0.3, sin bkb)
        assertThat(derivado.peso()).isCloseTo(0.25 * 0.6 * FiabilidadHerramienta.calcular(null, 0.3, 0, false, false), within(1e-9));
        // peso("baja") 0.25 x HEX/ESCAPE 1.0 x fiabilidad(2 s, instantáneo)  (no el peso "alta" de la herramienta)
        assertThat(hex.peso()).isCloseTo(0.25 * 1.0 * 0.75, within(1e-9));
        assertThat(hex.motivo()).startsWith("Anti-Mage depende de Blink (escape): Hex de Lion lo desactiva 2 s en su primer nivel");
    }

    @Test
    void laHerramientaDerivadaSobreUnEnemigoCuradoUsaElMotivoSinNumerosInventados() throws IOException {
        Contrarresto c = de(tablaReal().contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.HUSKAR, datos), Mecanismo.ESCAPE);

        assertThat(c.motivo()).isEqualTo("Storm Spirit depende de Ball Lightning (escape): Inner Fire de Huskar lo "
                + "desactiva (disable duro), lanzamiento de 0,35 s.");
        // peso("media") 0.5 x 0.6 x fiabilidad(sin duración conocida)
        assertThat(c.peso()).isCloseTo(0.5 * 0.6 * FiabilidadHerramienta.calcular(null, 0.35, 0, false, false), within(1e-9));
    }

    // --- movimientoNoEscape ---

    @Test
    void unMovimientoListadoEnMovimientoNoEscapeNoEsUnEscape() throws IOException {
        String sinLista = SIN_CURADOS;
        String conLista = "{\"dependencias\":[],\"herramientas\":[],\"movimientoNoEscape\":[\"puck_ethereal_jaunt\"]}";

        assertThat(tabla(sinLista).contrarrestos(PUCK, SVEN, datos)).hasSize(1);
        TablaContrarrestos tabla = tabla(conLista);
        assertThat(tabla.contrarrestos(PUCK, SVEN, datos)).isEmpty();
        assertThat(tabla.problemas(datos)).isEmpty();
    }

    @Test
    void unaClaveDesconocidaEnMovimientoNoEscapeVaAProblemasYNoRompe() throws IOException {
        TablaContrarrestos tabla = tabla(
                "{\"dependencias\":[],\"herramientas\":[],\"movimientoNoEscape\":[\"habilidad_inventada\"]}");

        assertThat(tabla.disponible(datos)).isTrue();
        assertThat(tabla.contrarrestos(MuestraOpenDota.ANTI_MAGE, SVEN, datos)).hasSize(1);
        assertThat(tabla.problemas(datos)).singleElement().asString()
                .contains("movimientoNoEscape").contains("habilidad_inventada");
    }

    // --- Curado gana ---

    @Test
    void unEnemigoConDependenciasCuradasIgnoraLasDerivadas() throws IOException {
        // Huskar está curado (solo PASIVAS): su Inner Fire marcado como "root disables" NO genera un ESCAPE derivado
        // y su pasiva rompible no genera una dependencia derivada extra.
        TablaContrarrestos real = tablaReal();

        List<Contrarresto> contraHuskar = real.contrarrestos(MuestraOpenDota.HUSKAR, SVEN, datos);

        assertThat(contraHuskar).isEmpty(); // Sven solo trae herramienta derivada de escape; Huskar no depende de eso

        // Sin la entrada curada de Huskar, el mismo indicador sí deriva un escape.
        TablaContrarrestos sinCurado = tabla(SIN_CURADOS);
        assertThat(de(sinCurado.contrarrestos(MuestraOpenDota.HUSKAR, SVEN, datos), Mecanismo.ESCAPE).motivo())
                .startsWith("Huskar depende de Inner Fire (escape)");
    }

    @Test
    void siLaMismaHabilidadEsHerramientaCuradaYDerivadaGanaLaCurada() throws IOException {
        TablaContrarrestos tabla = tabla("""
                {"dependencias":[],
                 "herramientas":[{"heroe":"Sven","herramientas":[
                   {"habilidad":"sven_storm_bolt","efecto":"STUN","claveDuracion":"bolt_stun_duration","confianza":"alta"}]}]}
                """);

        List<Contrarresto> sven = tabla.contrarrestos(MuestraOpenDota.ANTI_MAGE, SVEN, datos);

        Contrarresto escape = de(sven, Mecanismo.ESCAPE);
        assertThat(escape.motivo()).contains("lo aturde 1 s en su primer nivel").doesNotContain("disable duro");
        assertThat(sven).hasSize(1);
        // peso("baja") 0.25 x 0.6 x fiabilidad(1 s, lanzamiento 0.2)
        assertThat(escape.peso()).isCloseTo(0.25 * 0.6 * FiabilidadHerramienta.calcular(1.0, 0.2, 0, false, false), within(1e-9));
    }

    // --- Qué NO es una herramienta derivada ---

    @Test
    void unaHabilidadOcultaONoDelHeroeNoEsHerramientaDerivada() throws IOException {
        // Lion: su único disable duro está marcado como oculto.
        indicadores.put("npc_dota_hero_lion", Map.of("lion_impale", ind(false, true, false, false, true)));
        TablaContrarrestos tabla = tabla(SIN_CURADOS);

        assertThat(tabla.contrarrestos(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION, datos)).isEmpty();
        // Sven: "lion_voodoo" viene en sus indicadores pero no es suya; la herramienta es Storm Hammer, no Hex.
        assertThat(de(tabla.contrarrestos(MuestraOpenDota.ANTI_MAGE, SVEN, datos), Mecanismo.ESCAPE).motivo())
                .contains("Storm Hammer").doesNotContain("Hex");
    }

    @Test
    void laHerramientaDerivadaSoloContrarrestaLoQueStunYaContrarrestaEnLaTabla() throws IOException {
        // Contra Phantom Lancer (curado: ILUSIONES y ESCAPE) un stun solo aporta contra el escape.
        TablaContrarrestos tabla = tablaReal();

        List<Contrarresto> sven = tabla.contrarrestos(MuestraOpenDota.PHANTOM_LANCER, SVEN, datos);

        assertThat(sven).extracting(Contrarresto::mecanismo).containsExactly(Mecanismo.ESCAPE);
    }

    // --- PASIVAS derivadas ---

    @Test
    void laPasivaRompibleDeUnEnemigoSinCuradoSeDerivaComoPasivasYLaCuentaUnBreakCurado() throws IOException {
        List<Contrarresto> viper = tablaConPasivas().contrarrestos(NAGA, MuestraOpenDota.VIPER, datos);

        Contrarresto c = de(viper, Mecanismo.PASIVAS);
        assertThat(c.motivo()).startsWith("Naga Siren depende de Eelskin (pasivas): Viper Strike de Viper");
        assertThat(c.efecto()).isEqualTo(Efecto.BREAK);
        // Dependencia derivada (baja) x herramienta curada (media) -> baja
        assertThat(c.confianza()).isEqualTo("baja");
    }

    @Test
    void laPasivaRompibleNoSeDerivaPorDefectoPorqueRepetiriaLasMismasHerramientasEnTodosLosHeroes() throws IOException {
        TablaContrarrestos tabla = tablaReal();

        assertThat(tabla.contrarrestos(NAGA, MuestraOpenDota.VIPER, datos)).isEmpty();
        assertThat(tabla.cubre(NAGA, datos)).isFalse();
        // El escape sigue derivándose: Anti-Mage tiene Blink y su Mana Break rompible no aporta una dependencia.
        assertThat(tabla.contrarrestos(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.VIPER, datos)).isEmpty();
    }

    @Test
    void conLasPasivasEncendidasUnHeroeConSoloUnaPasivaRompibleQuedaCubierto() throws IOException {
        assertThat(tablaConPasivas().cubre(NAGA, datos)).isTrue();
    }

    // --- Herramientas: solo disables que se lanzan contra enemigos ---

    @Test
    void unDisableDuroQueNoApuntaAEnemigosNoEsHerramientaDerivada() throws IOException {
        indicadores.put("npc_dota_hero_lion", Map.of("lion_impale",
                new IndicadoresHabilidad(false, true, false, false, false, false, false, false)));
        TablaContrarrestos tabla = tabla(SIN_CURADOS);

        assertThat(tabla.contrarrestos(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION, datos)).isEmpty();
    }

    // --- La tabla no se queda para siempre sin los indicadores ---

    @Test
    void siLosIndicadoresLlegandespuesLaTablaSeReconstruyeUnaVezYNoAntes() throws IOException {
        indicadores = new HashMap<>(); // el calentamiento todavía no terminó (o GitHub estaba caído)
        TablaContrarrestos tabla = tablaReal();
        assertThat(tabla.cubre(MuestraOpenDota.ANTI_MAGE, datos)).isFalse();
        assertThat(tabla.hayIndicadoresDeJuego(datos)).isFalse();

        // Sin cambio de versión la tabla no se reconstruye aunque se consulte muchas veces.
        for (int i = 0; i < 10; i++) {
            tabla.cubre(MuestraOpenDota.ANTI_MAGE, datos);
        }
        verify(metaService, times(1)).datosHabilidad();

        // Llegan los indicadores: la versión cambia y la siguiente consulta reconstruye (una sola vez).
        indicadores = new HashMap<>(Map.of("npc_dota_hero_antimage", Map.of("antimage_blink", ESCAPE),
                "npc_dota_hero_lion", Map.of("lion_voodoo", DISABLE_DURO)));
        publicarIndicadores(1L);

        assertThat(tabla.cubre(MuestraOpenDota.ANTI_MAGE, datos)).isTrue();
        assertThat(tabla.contrarrestos(MuestraOpenDota.ANTI_MAGE, MuestraOpenDota.LION, datos)).isNotEmpty();
        verify(metaService, times(2)).datosHabilidad();
    }

    // --- Cuándo la tabla "cubre" a un enemigo ---

    @Test
    void laTablaCubreAlEnemigoCuradoYAlQueTieneIndicadoresPeroNoAlQueNoLosTiene() throws IOException {
        TablaContrarrestos tabla = tablaReal();

        assertThat(tabla.cubre(MuestraOpenDota.STORM_SPIRIT, datos)).isTrue(); // curado
        assertThat(tabla.cubre(MuestraOpenDota.ANTI_MAGE, datos)).isTrue();    // indicadores del juego
        assertThat(tabla.cubre(MuestraOpenDota.ZEUS, datos)).isFalse();        // sin indicadores: vuelve a lo automático
        assertThat(tabla.hayIndicadoresDeJuego(datos)).isTrue();
    }

    @Test
    void unEnemigoConIndicadoresPeroSinNingunaDependenciaDerivadaNoSeCubreYVuelveAlDetectorPorPalabraClave() throws IOException {
        // Lion no tiene blinks (y con las pasivas apagadas no hay nada más que derivar): la tabla NO lo cubre. Si lo
        // cubriera, un héroe sin escape (invisibles, ilusiones...) perdería la fila por palabra clave de §5.1.
        TablaContrarrestos tabla = tablaReal();

        assertThat(tabla.cubre(MuestraOpenDota.LION, datos)).isFalse();
        assertThat(tabla.contrarrestos(MuestraOpenDota.LION, SVEN, datos)).isEmpty();
    }

    @Test
    void sinIndicadoresDelJuegoSoloLosEnemigosCuradosEstanCubiertosYTodoSigueComoAntes() throws IOException {
        indicadores = new HashMap<>();
        TablaContrarrestos tabla = tablaReal();

        assertThat(tabla.hayIndicadoresDeJuego(datos)).isFalse();
        assertThat(tabla.cubre(MuestraOpenDota.STORM_SPIRIT, datos)).isTrue();
        assertThat(tabla.cubre(MuestraOpenDota.ANTI_MAGE, datos)).isFalse();
        assertThat(tabla.contrarrestos(MuestraOpenDota.ANTI_MAGE, SVEN, datos)).isEmpty();
        assertThat(tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos)).isNotEmpty();
    }

    @Test
    void elArchivoCuradoQueTraeElJarNoTieneProblemasConLosIndicadoresDerivados() throws IOException {
        assertThat(tablaReal().problemas(datos)).isEmpty();
    }
}
