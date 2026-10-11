package com.dotastats.analizadores.habilidad.dependencias;

import com.dotastats.analizadores.habilidad.HabilidadProperties;
import com.dotastats.exception.OpenDotaNoDisponibleException;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TablaContrarrestosTest {

    @TempDir
    Path tmp;

    private MuestraOpenDota muestra;
    private DatosMeta datos;
    private DatosMetaService metaService;
    private HabilidadProperties props;

    @BeforeEach
    void preparar() {
        muestra = MuestraOpenDota.cargar();
        datos = muestra.datosMeta();
        metaService = mock(DatosMetaService.class);
        when(metaService.datosHabilidad()).thenReturn(muestra.datosHabilidad());
        props = new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null, null);
    }

    /** Tabla con el archivo curado que trae el jar. */
    private TablaContrarrestos tablaReal() {
        RepositorioDependencias repo = new RepositorioDependencias(new DefaultResourceLoader(), props);
        repo.cargar();
        return new TablaContrarrestos(metaService, repo, props);
    }

    /** Tabla con un archivo curado propio (para probar casos concretos sin depender del archivo del jar). */
    private TablaContrarrestos tablaCon(String json) throws IOException {
        return tablaCon(json, 50);
    }

    private TablaContrarrestos tablaCon(String json, int rangoMinimo) throws IOException {
        Path archivo = tmp.resolve("dependencias.json");
        Files.writeString(archivo, json, StandardCharsets.UTF_8);
        HabilidadProperties propias = new HabilidadProperties(1.0, 0.5, 0.25, "classpath:reglas-habilidades.json", null,
                new HabilidadProperties.Dependencias("file:" + archivo.toAbsolutePath(), rangoMinimo));
        RepositorioDependencias repo = new RepositorioDependencias(new DefaultResourceLoader(), propias);
        repo.cargar();
        return new TablaContrarrestos(metaService, repo, propias);
    }

    private static Contrarresto de(List<Contrarresto> lista, Mecanismo mecanismo) {
        return lista.stream().filter(c -> c.mecanismo() == mecanismo).findFirst().orElseThrow();
    }

    // --- Storm Spirit: contrarrestar la dependencia (escape), no al héroe ---

    @Test
    void elHexDeLionContraElEscapeDeStormCitaLaDependenciaYLosNumerosDeNivel1() {
        TablaContrarrestos tabla = tablaReal();

        List<Contrarresto> lion = tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos);

        Contrarresto escape = de(lion, Mecanismo.ESCAPE);
        assertThat(escape.motivo()).isEqualTo("Storm Spirit depende de Ball Lightning (escape): Hex de Lion "
                + "lo desactiva 2 s en su primer nivel, lanzamiento instantáneo.");
        assertThat(escape.efecto()).isEqualTo(Efecto.HEX);
        assertThat(escape.habilidad()).isEqualTo("lion_voodoo");
        assertThat(escape.motivo()).doesNotContain("nivel 6");
        assertThat(escape.confianza()).isEqualTo("alta");
        assertThat(escape.esUlti()).isFalse();
        // peso = peso("alta") 1.0 x multiplicador HEX/ESCAPE 1.0 x fiabilidad(2 s, lanzamiento 0, sin bkb) 0.75
        assertThat(escape.peso()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void lionTambienCastigaLaDependenciaDelMana() {
        List<Contrarresto> lion = tablaReal().contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos);

        Contrarresto mana = de(lion, Mecanismo.MANA);

        assertThat(mana.motivo()).startsWith("Storm Spirit depende de Ball Lightning (maná): Mana Drain de Lion")
                .contains("20 en su primer nivel");
        assertThat(mana.efecto()).isEqualTo(Efecto.QUEMA_MANA);
    }

    @Test
    void huskarNoEsContraparteDelEscapeAunqueLaDescripcionDeInnerFireDigaSilencio() throws IOException {
        // Inner Fire dice "silencing" en la descripción, pero sus atributos solo traen disarm_duration
        // (no silence_duration): un SILENCIO curado sobre esa clave NO se respalda y se descarta.
        TablaContrarrestos tabla = tablaCon("""
                {"dependencias":[{"heroe":"Storm Spirit","dependencias":[
                   {"mecanismo":"ESCAPE","habilidad":"storm_spirit_ball_lightning","descripcion":"escape"}]}],
                 "herramientas":[{"heroe":"Huskar","herramientas":[
                   {"habilidad":"huskar_inner_fire","efecto":"SILENCIO","claveDuracion":"silence_duration","confianza":"media"}]}]}
                """);

        List<Contrarresto> huskar = tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.HUSKAR, datos);

        assertThat(huskar).isEmpty();
        assertThat(tabla.problemas(datos)).singleElement().asString()
                .contains("Huskar").contains("huskar_inner_fire").contains("silence_duration");
    }

    @Test
    void elDesarmeDeInnerFireSiSeRespaldaConSuAtributoDisarmDuration() throws IOException {
        TablaContrarrestos tabla = tablaCon("""
                {"dependencias":[{"heroe":"Viper","dependencias":[
                   {"mecanismo":"PASIVAS","habilidad":"viper_poison_attack","descripcion":"pasivas"}]}],
                 "herramientas":[{"heroe":"Huskar","herramientas":[
                   {"habilidad":"huskar_inner_fire","efecto":"DESARME","claveDuracion":"disarm_duration","confianza":"media"}]}]}
                """);

        List<Contrarresto> huskar = tabla.contrarrestos(MuestraOpenDota.VIPER, MuestraOpenDota.HUSKAR, datos);

        assertThat(tabla.problemas(datos)).isEmpty();
        assertThat(huskar).singleElement().satisfies(c -> {
            assertThat(c.efecto()).isEqualTo(Efecto.DESARME);
            assertThat(c.motivo()).contains("Inner Fire de Huskar").contains("lo desarma 1,5 s en su primer nivel");
        });
    }

    @Test
    void entreVariasHerramientasContraElMismoMecanismoSoloCuentaLaMejor() throws IOException {
        TablaContrarrestos tabla = tablaCon("""
                {"dependencias":[{"heroe":"Storm Spirit","dependencias":[
                   {"mecanismo":"ESCAPE","habilidad":"storm_spirit_ball_lightning","descripcion":"escape"}]}],
                 "herramientas":[{"heroe":"Lion","herramientas":[
                   {"habilidad":"lion_impale","efecto":"STUN","claveDuracion":"duration","confianza":"media"},
                   {"habilidad":"lion_voodoo","efecto":"HEX","claveDuracion":"duration","confianza":"alta"}]}]}
                """);

        List<Contrarresto> lion = tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos);

        assertThat(lion).singleElement().satisfies(c -> assertThat(c.efecto()).isEqualTo(Efecto.HEX));
    }

    // --- Riki, Phantom Lancer, Huskar, Viper ---

    @Test
    void contraRikiUnaHerramientaDeVisionVerdaderaContrarrestaLaInvisibilidad() {
        TablaContrarrestos tabla = tablaReal();

        Contrarresto zeus = de(tabla.contrarrestos(MuestraOpenDota.RIKI, MuestraOpenDota.ZEUS, datos), Mecanismo.INVISIBILIDAD);
        Contrarresto drow = de(tabla.contrarrestos(MuestraOpenDota.RIKI, MuestraOpenDota.DROW, datos), Mecanismo.INVISIBILIDAD);

        assertThat(zeus.efecto()).isEqualTo(Efecto.REVELADO);
        assertThat(zeus.motivo()).isEqualTo("Riki depende de Cloak and Dagger (invisibilidad): Lightning Bolt de Zeus "
                + "revela su invisibilidad, lanzamiento de 0,3 s.");
        assertThat(drow.efecto()).isEqualTo(Efecto.REVELADO);
        assertThat(drow.motivo()).contains("Gust de Drow Ranger revela su invisibilidad");
    }

    @Test
    void unAreaTambienContrarrestaALikiPeroPesaMenosQueRevelar() {
        TablaContrarrestos tabla = tablaReal();

        Contrarresto leshrac = de(tabla.contrarrestos(MuestraOpenDota.RIKI, MuestraOpenDota.LESHRAC, datos), Mecanismo.INVISIBILIDAD);
        Contrarresto zeus = de(tabla.contrarrestos(MuestraOpenDota.RIKI, MuestraOpenDota.ZEUS, datos), Mecanismo.INVISIBILIDAD);

        assertThat(leshrac.efecto()).isEqualTo(Efecto.AREA);
        assertThat(leshrac.motivo()).contains("daña en área (radio 450 en su primer nivel)");
        assertThat(zeus.peso()).isGreaterThan(leshrac.peso());
    }

    @Test
    void laUltimateDeRevelarSeMarcaYSePenaliza() {
        TablaContrarrestos tabla = tablaReal();

        Contrarresto slardar = de(tabla.contrarrestos(MuestraOpenDota.RIKI, MuestraOpenDota.SLARDAR, datos), Mecanismo.INVISIBILIDAD);

        assertThat(slardar.esUlti()).isTrue();
        assertThat(slardar.motivo()).endsWith("atraviesa la inmunidad a la magia (desde nivel 6).");
        assertThat(slardar.confianza()).isEqualTo("media");
        // peso("media") 0.5 x 1.0 x fiabilidad(18 s -> tope 3 s, lanzamiento 0.3, bkb Yes, ulti) = 0.5 x (0.4+0.3+0.10+0.15) x 0.5
        assertThat(slardar.peso()).isCloseTo(0.5 * 1.0 * 0.95 * 0.5, within(1e-9));
    }

    @Test
    void contraPhantomLancerLasAreasLimpianIlusionesYUnHexLeQuitaElEscape() {
        TablaContrarrestos tabla = tablaReal();

        List<Contrarresto> leshrac = tabla.contrarrestos(MuestraOpenDota.PHANTOM_LANCER, MuestraOpenDota.LESHRAC, datos);
        List<Contrarresto> lion = tabla.contrarrestos(MuestraOpenDota.PHANTOM_LANCER, MuestraOpenDota.LION, datos);

        assertThat(de(leshrac, Mecanismo.ILUSIONES).motivo())
                .startsWith("Phantom Lancer depende de Juxtapose (ilusiones): Diabolic Edict de Leshrac");
        assertThat(de(lion, Mecanismo.ESCAPE).motivo())
                .startsWith("Phantom Lancer depende de Doppelganger (escape): Hex de Lion");
    }

    @Test
    void contraHuskarElBreakDeViperStrikeEsUltimateYContrarrestaLasPasivas() {
        List<Contrarresto> viper = tablaReal().contrarrestos(MuestraOpenDota.HUSKAR, MuestraOpenDota.VIPER, datos);

        Contrarresto c = de(viper, Mecanismo.PASIVAS);

        assertThat(c.efecto()).isEqualTo(Efecto.BREAK);
        assertThat(c.esUlti()).isTrue();
        assertThat(c.motivo()).endsWith(" (desde nivel 6).");
        assertThat(c.motivo()).startsWith("Huskar depende de Berserker's Blood (pasivas): Viper Strike de Viper");
        // 0.5 x 1.0 x [(0.4 + 0.3 (6 s -> tope) + 0.10 (0.2) + 0.15 bkb) x 0.5]
        assertThat(c.peso()).isCloseTo(0.5 * 0.95 * 0.5, within(1e-9));
    }

    @Test
    void elRangoMayorSaleDelAlcanceDeAtaqueDeLasEstadisticasSinCurarlo() {
        TablaContrarrestos tabla = tablaReal();

        List<Contrarresto> drow = tabla.contrarrestos(MuestraOpenDota.VIPER, MuestraOpenDota.DROW, datos);

        Contrarresto c = de(drow, Mecanismo.ACOSO_RANGO);
        assertThat(c.efecto()).isEqualTo(Efecto.RANGO_MAYOR);
        assertThat(c.motivo()).isEqualTo("Viper depende de Poison Attack (acoso a distancia): Drow Ranger tiene más "
                + "alcance de ataque (625 frente a 575).");
        assertThat(c.confianza()).isEqualTo("media");
        assertThat(c.esUlti()).isFalse();
        // 0.5 x 0.6 x deAlcance(50) = 0.5 x 0.6 x 0.6
        assertThat(c.peso()).isCloseTo(0.5 * 0.6 * FiabilidadHerramienta.deAlcance(50), within(1e-9));
    }

    @Test
    void unCandidatoConMenosAlcanceQueElEnemigoNoTieneRangoMayor() {
        TablaContrarrestos tabla = tablaReal();

        // Storm Spirit (480) < Viper (575); Anti-Mage (150) también.
        assertThat(tabla.contrarrestos(MuestraOpenDota.VIPER, MuestraOpenDota.STORM_SPIRIT, datos)).isEmpty();
        assertThat(tabla.contrarrestos(MuestraOpenDota.VIPER, MuestraOpenDota.ANTI_MAGE, datos)).isEmpty();
    }

    @Test
    void unaVentajaDeAlcanceMenorQueElMinimoConfiguradoNoCalifica() {
        // Lion (600) solo le saca 25 a Viper (575): con el mínimo por defecto (50) no cuenta.
        assertThat(tablaReal().contrarrestos(MuestraOpenDota.VIPER, MuestraOpenDota.LION, datos)).isEmpty();
        // Drow (625) le saca exactamente 50: la diferencia debe ser >= al mínimo.
        assertThat(tablaReal().contrarrestos(MuestraOpenDota.VIPER, MuestraOpenDota.DROW, datos)).hasSize(1);
    }

    @Test
    void elMinimoDeAlcanceEsConfigurable() throws IOException {
        String json = """
                {"dependencias":[{"heroe":"Viper","dependencias":[
                   {"mecanismo":"ACOSO_RANGO","habilidad":"viper_poison_attack","descripcion":"acoso"}]}],
                 "herramientas":[]}
                """;

        assertThat(tablaCon(json, 25).contrarrestos(MuestraOpenDota.VIPER, MuestraOpenDota.LION, datos))
                .singleElement().satisfies(c -> assertThat(c.motivo()).contains("600 frente a 575"));
        assertThat(tablaCon(json, 100).contrarrestos(MuestraOpenDota.VIPER, MuestraOpenDota.DROW, datos)).isEmpty();
    }

    @Test
    void sinAlcanceDeAtaqueNoHayHerramientaDeRango() {
        when(metaService.datosHabilidad()).thenReturn(new com.dotastats.meta.DatosHabilidad(
                muestra.datosHabilidad().abilidades(), muestra.datosHabilidad().habilidadesPorHeroe()));

        assertThat(tablaReal().contrarrestos(MuestraOpenDota.VIPER, MuestraOpenDota.DROW, datos)).isEmpty();
    }

    @Test
    void losDecimalesSeEscribenConComaEnEspanol() {
        Contrarresto dp = de(tablaReal().contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.DEATH_PROPHET, datos),
                Mecanismo.ESCAPE);

        assertThat(dp.motivo()).contains("lo silencia 3,5 s en su primer nivel");
    }

    @Test
    void sinDependenciasCuradasElEnemigoNoTieneContrarrestos() {
        assertThat(tablaReal().contrarrestos(MuestraOpenDota.LION, MuestraOpenDota.DROW, datos)).isEmpty();
    }

    // --- Validación contra el snapshot ---

    @Test
    void unaClaveDeHabilidadDesconocidaSeReportaClaramenteYSeDescartaLaHerramienta() throws IOException {
        TablaContrarrestos tabla = tablaCon("""
                {"dependencias":[{"heroe":"Storm Spirit","dependencias":[
                   {"mecanismo":"ESCAPE","habilidad":"storm_spirit_ball_lightning","descripcion":"escape"}]}],
                 "herramientas":[{"heroe":"Lion","herramientas":[
                   {"habilidad":"lion_hechizo_inventado","efecto":"HEX","claveDuracion":"duration","confianza":"alta"}]}]}
                """);

        assertThat(tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos)).isEmpty();
        assertThat(tabla.problemas(datos)).singleElement().asString()
                .contains("lion_hechizo_inventado").contains("Lion").contains("desconocida");
    }

    @Test
    void unaDependenciaConUnaClaveDeHabilidadDesconocidaSeReporta() throws IOException {
        TablaContrarrestos tabla = tablaCon("""
                {"dependencias":[{"heroe":"Storm Spirit","dependencias":[
                   {"mecanismo":"ESCAPE","habilidad":"storm_spirit_no_existe","descripcion":"escape"}]}],
                 "herramientas":[]}
                """);

        assertThat(tabla.problemas(datos)).singleElement().asString()
                .contains("storm_spirit_no_existe").contains("Storm Spirit");
    }

    @Test
    void unaHabilidadQueNoEsDelHeroeSeReporta() throws IOException {
        TablaContrarrestos tabla = tablaCon("""
                {"dependencias":[{"heroe":"Storm Spirit","dependencias":[
                   {"mecanismo":"ESCAPE","habilidad":"storm_spirit_ball_lightning","descripcion":"escape"}]}],
                 "herramientas":[{"heroe":"Lion","herramientas":[
                   {"habilidad":"sven_storm_bolt","efecto":"STUN","claveDuracion":"bolt_stun_duration","confianza":"media"}]}]}
                """);

        assertThat(tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos)).isEmpty();
        assertThat(tabla.problemas(datos)).singleElement().asString()
                .contains("sven_storm_bolt").contains("no pertenece").contains("Lion");
    }

    @Test
    void unaClaveDeDuracionQueNoExisteEnLosAtributosSeReporta() throws IOException {
        TablaContrarrestos tabla = tablaCon("""
                {"dependencias":[{"heroe":"Storm Spirit","dependencias":[
                   {"mecanismo":"ESCAPE","habilidad":"storm_spirit_ball_lightning","descripcion":"escape"}]}],
                 "herramientas":[{"heroe":"Lion","herramientas":[
                   {"habilidad":"lion_voodoo","efecto":"HEX","claveDuracion":"hex_duration","confianza":"alta"}]}]}
                """);

        assertThat(tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos)).isEmpty();
        assertThat(tabla.problemas(datos)).singleElement().asString().contains("hex_duration").contains("lion_voodoo");
    }

    @Test
    void unNombreDeHeroeDesconocidoSeSaltaSeReportaYNoRompeLaTabla() throws IOException {
        TablaContrarrestos tabla = tablaCon("""
                {"dependencias":[{"heroe":"Storm Spirit","dependencias":[
                   {"mecanismo":"ESCAPE","habilidad":"storm_spirit_ball_lightning","descripcion":"escape"}]}],
                 "herramientas":[
                   {"heroe":"Leon","herramientas":[
                     {"habilidad":"lion_voodoo","efecto":"HEX","claveDuracion":"duration","confianza":"alta"}]},
                   {"heroe":"Lion","herramientas":[
                     {"habilidad":"lion_voodoo","efecto":"HEX","claveDuracion":"duration","confianza":"alta"}]}]}
                """);

        assertThat(tabla.disponible(datos)).isTrue();
        assertThat(tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos)).hasSize(1);
        assertThat(tabla.problemas(datos)).singleElement().asString().contains("Leon").contains("herramientas");
    }

    @Test
    void unNombreDeHeroeDesconocidoTambienSeReportaSiLaTablaNoEstaDisponible() throws IOException {
        when(metaService.datosHabilidad()).thenThrow(new OpenDotaNoDisponibleException("caído"));
        TablaContrarrestos tabla = tablaCon("""
                {"dependencias":[{"heroe":"Stormm Spirit","dependencias":[
                   {"mecanismo":"ESCAPE","descripcion":"escape"}]}],"herramientas":[]}
                """);

        assertThat(tabla.disponible(datos)).isFalse();
        assertThat(tabla.problemas(datos)).singleElement().asString().contains("Stormm Spirit");
    }

    @Test
    void elArchivoCuradoQueTraeElJarNoTieneNingunProblemaContraLosDatosReales() {
        TablaContrarrestos tabla = tablaReal();

        assertThat(tabla.disponible(datos)).isTrue();
        assertThat(tabla.problemas(datos)).isEmpty();
    }

    // --- Se construye una vez; en la petición solo se consulta ---

    @Test
    void laTablaSeConstruyeUnaSolaVezPorSnapshotYLasConsultasNoTocanOpenDota() {
        TablaContrarrestos tabla = tablaReal();

        for (int i = 0; i < 25; i++) {
            tabla.disponible(datos);
            tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos);
            tabla.contrarrestos(MuestraOpenDota.RIKI, MuestraOpenDota.ZEUS, datos);
        }

        verify(metaService, times(1)).datosHabilidad();
        verify(metaService, org.mockito.Mockito.atLeastOnce()).versionIndicadoresJuego();
        verifyNoMoreInteractions(metaService);
    }

    @Test
    void conUnSnapshotNuevoLaTablaSeReconstruye() {
        TablaContrarrestos tabla = tablaReal();
        tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos);

        tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, muestra.otroSnapshot());

        verify(metaService, times(2)).datosHabilidad();
    }

    @Test
    void siOpenDotaNoDaLasHabilidadesLaTablaQuedaNoDisponibleYNoReintentaConElMismoSnapshot() {
        when(metaService.datosHabilidad()).thenThrow(new OpenDotaNoDisponibleException("caído"));
        TablaContrarrestos tabla = tablaReal();

        assertThat(tabla.disponible(datos)).isFalse();
        assertThat(tabla.contrarrestos(MuestraOpenDota.STORM_SPIRIT, MuestraOpenDota.LION, datos)).isEmpty();
        assertThat(tabla.disponible(datos)).isFalse();

        verify(metaService, times(1)).datosHabilidad();
    }
}
