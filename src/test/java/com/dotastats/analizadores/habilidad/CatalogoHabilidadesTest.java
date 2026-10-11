package com.dotastats.analizadores.habilidad;

import com.dotastats.client.Parche;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.meta.DatosHabilidad;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.HeroeMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Nunca toca la red: {@link DatosMetaService} está mockeado. */
class CatalogoHabilidadesTest {

    private static final HeroeMeta LION = new HeroeMeta(26, "Lion", Set.of(), 1000, 500, "npc_dota_hero_lion");
    private static final HeroeMeta STORM = new HeroeMeta(6, "Storm Spirit", Set.of(), 1000, 500,
            "npc_dota_hero_storm_spirit");
    private static final HeroeMeta SIN_NOMBRE_INTERNO = new HeroeMeta(999, "Otro", Set.of(), 1000, 500);

    private DatosMetaService metaService;
    private DatosMeta datos;
    private CatalogoHabilidades catalogo;

    @BeforeEach
    void preparar() {
        metaService = mock(DatosMetaService.class);
        datos = new DatosMeta(Map.of(26, LION, 6, STORM, 999, SIN_NOMBRE_INTERNO),
                new Parche(60, "7.41"), Instant.parse("2026-10-10T12:00:00Z"));
        catalogo = new CatalogoHabilidades(metaService);
    }

    private void datosDisponibles(Map<String, Object> abilidades, Map<String, Object> porHeroe) {
        when(metaService.datosHabilidad()).thenReturn(new DatosHabilidad(abilidades, porHeroe));
    }

    @Test
    void detectaElRasgoEscapeDeUnaHabilidadDeMovimiento() {
        datosDisponibles(
                Map.of("storm_spirit_ball_lightning", Map.of("dname", "Ball Lightning", "behavior", "Point Target",
                        "desc", "Storm Spirit becomes volatile electricity, charging across the battlefield "
                                + "until he depletes his mana or reaches his target.")),
                Map.of("npc_dota_hero_storm_spirit", Map.of("abilities", List.of("storm_spirit_ball_lightning"))));

        Optional<PerfilHabilidadesHeroe> perfil = catalogo.perfilDe(6, datos);

        assertThat(perfil).isPresent();
        assertThat(perfil.get().rasgos()).containsKey(Rasgo.ESCAPE);
        assertThat(perfil.get().rasgos().get(Rasgo.ESCAPE).get(0).nombre()).isEqualTo("Ball Lightning");
    }

    @Test
    void detectaLaCapacidadHexDeUnaHabilidadDelCandidato() {
        datosDisponibles(
                Map.of("lion_voodoo", Map.of("dname", "Hex", "behavior", "Unit Target", "target_team", "Enemy",
                        "desc", "Transforms an enemy unit into a harmless beast.", "cd", "25", "mc", "120")),
                Map.of("npc_dota_hero_lion", Map.of("abilities", List.of("lion_voodoo"))));

        Optional<PerfilHabilidadesHeroe> perfil = catalogo.perfilDe(26, datos);

        assertThat(perfil).isPresent();
        assertThat(perfil.get().capacidades()).containsKey(Capacidad.HEX);
        assertThat(perfil.get().capacidades().get(Capacidad.HEX).get(0).esUlti()).isFalse();
    }

    @Test
    void marcaEsUltiCuandoCdTieneTresNiveles() {
        datosDisponibles(
                Map.of("lion_voodoo", Map.of("dname", "Hex", "behavior", "Unit Target", "target_team", "Enemy",
                        "desc", "Transforms an enemy unit into a harmless beast.",
                        "cd", List.of("30", "25", "20"), "mc", "150")),
                Map.of("npc_dota_hero_lion", Map.of("abilities", List.of("lion_voodoo"))));

        Optional<PerfilHabilidadesHeroe> perfil = catalogo.perfilDe(26, datos);

        assertThat(perfil.get().capacidades().get(Capacidad.HEX).get(0).esUlti()).isTrue();
    }

    @Test
    void ignoraGenericHiddenYLosTalentosSpecialBonus() {
        datosDisponibles(
                Map.of("lion_voodoo", Map.of("dname", "Hex", "behavior", "Unit Target", "target_team", "Enemy",
                        "desc", "Transforms an enemy unit into a harmless beast.")),
                Map.of("npc_dota_hero_lion", Map.of("abilities",
                        List.of("generic_hidden", "special_bonus_unique_lion_1", "lion_voodoo"))));

        Optional<PerfilHabilidadesHeroe> perfil = catalogo.perfilDe(26, datos);

        assertThat(perfil.get().capacidades().get(Capacidad.HEX)).hasSize(1);
    }

    @Test
    void unHeroeSinNombreInternoNoTienePerfil() {
        datosDisponibles(Map.of(), Map.of());

        assertThat(catalogo.perfilDe(999, datos)).isEmpty();
    }

    @Test
    void sinPerfilParaUnHeroeDesconocidoEnHeroAbilitiesDevuelveVacio() {
        datosDisponibles(Map.of(), Map.of());

        assertThat(catalogo.perfilDe(26, datos)).isEmpty();
    }

    @Test
    void siElClienteFallaElCatalogoQuedaNoDisponibleYNoFallaLaPeticion() {
        when(metaService.datosHabilidad()).thenThrow(new OpenDotaNoDisponibleException("caído"));

        assertThat(catalogo.disponible(datos)).isFalse();
        assertThat(catalogo.perfilDe(26, datos)).isEmpty();
    }

    @Test
    void disponibleEsVerdaderoCuandoElCatalogoSeResuelveBien() {
        datosDisponibles(Map.of(), Map.of());

        assertThat(catalogo.disponible(datos)).isTrue();
    }

    @Test
    void entreVariasHabilidadesConElMismoRasgoLaUltiVaPrimero() {
        // El héroe tiene DOS habilidades con rasgo ESCAPE; en el array de hero_abilities la NO-ulti viene
        // primero, pero el perfil debe nombrar la ulti primero (mayor peso de evidencia).
        datosDisponibles(
                Map.of(
                        "x_dash", Map.of("dname", "Dash", "behavior", "Point Target", "desc", "Dashes forward."),
                        "x_blink_ulti", Map.of("dname", "Gran Blink", "behavior", "Point Target",
                                "desc", "Teleports a great distance.", "cd", List.of("30", "25", "20"))),
                Map.of("npc_dota_hero_lion", Map.of("abilities", List.of("x_dash", "x_blink_ulti"))));

        PerfilHabilidadesHeroe perfil = catalogo.perfilDe(26, datos).orElseThrow();

        assertThat(perfil.rasgos().get(Rasgo.ESCAPE)).hasSize(2);
        assertThat(perfil.rasgos().get(Rasgo.ESCAPE).get(0).esUlti()).isTrue();
        assertThat(perfil.rasgos().get(Rasgo.ESCAPE).get(0).nombre()).isEqualTo("Gran Blink");
    }

    @Test
    void soloSeConsultaUnaVezPorSnapshotDeDatos() {
        datosDisponibles(Map.of(), Map.of());

        catalogo.perfilDe(26, datos);
        catalogo.perfilDe(6, datos);
        catalogo.disponible(datos);

        org.mockito.Mockito.verify(metaService, org.mockito.Mockito.times(1)).datosHabilidad();
    }
}
