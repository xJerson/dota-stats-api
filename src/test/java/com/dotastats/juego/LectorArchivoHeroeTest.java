package com.dotastats.juego;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sin red: el archivo de muestra es un recorte de archivos reales de d2vpkr (Lion, Storm Spirit, Huskar). */
class LectorArchivoHeroeTest {

    private static String muestra() {
        try (InputStream in = LectorArchivoHeroeTest.class.getResourceAsStream("/juego/heroe-muestra.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void ballLightningSeMarcaComoDesactivadaPorRootYComoUltimate() {
        Map<String, IndicadoresHabilidad> r = LectorArchivoHeroe.leer(muestra());

        IndicadoresHabilidad ball = r.get("storm_spirit_ball_lightning");
        assertThat(ball.desactivadaPorRoot()).isTrue();
        assertThat(ball.ultimate()).isTrue();
        assertThat(ball.disableDuro()).isFalse();
        assertThat(ball.rompible()).isFalse();
        assertThat(ball.pasiva()).isFalse();
        assertThat(ball.oculta()).isFalse();
    }

    @Test
    void elHexYElImpaleSonDisablesDurosPeroNoEstanDesactivadosPorRoot() {
        Map<String, IndicadoresHabilidad> r = LectorArchivoHeroe.leer(muestra());

        assertThat(r.get("lion_voodoo").disableDuro()).isTrue();
        assertThat(r.get("lion_voodoo").desactivadaPorRoot()).isFalse();
        assertThat(r.get("lion_voodoo").ultimate()).isFalse();
        assertThat(r.get("lion_impale").disableDuro()).isTrue();
        assertThat(r.get("lion_mana_drain").disableDuro()).isFalse();
    }

    @Test
    void manaDrainEsCanalizada() {
        assertThat(LectorArchivoHeroe.leer(muestra()).get("lion_mana_drain").canalizada()).isTrue();
        assertThat(LectorArchivoHeroe.leer(muestra()).get("lion_voodoo").canalizada()).isFalse();
    }

    @Test
    void berserkersBloodEsRompibleYPasiva() {
        IndicadoresHabilidad sangre = LectorArchivoHeroe.leer(muestra()).get("huskar_berserkers_blood");

        assertThat(sangre.rompible()).isTrue();
        assertThat(sangre.pasiva()).isTrue();
        assertThat(sangre.oculta()).isFalse();
        assertThat(sangre.desactivadaPorRoot()).isFalse();
    }

    @Test
    void lasHabilidadesOcultasNoLearnablesOInnateSeMarcanComoOcultas() {
        Map<String, IndicadoresHabilidad> r = LectorArchivoHeroe.leer(muestra());

        assertThat(r.get("generic_hidden").oculta()).isTrue();
        assertThat(r.get("lion_innate_de_prueba").oculta()).isTrue();
    }

    @Test
    void losBloquesSpecialBonusSeIgnoranAunqueTraigaAbilityBehavior() {
        Map<String, IndicadoresHabilidad> r = LectorArchivoHeroe.leer(muestra());

        assertThat(r).doesNotContainKey("special_bonus_unique_lion_6");
    }

    @Test
    void soloSonHabilidadesLosBloquesQueTraenAbilityBehaviorDirectamente() {
        Map<String, IndicadoresHabilidad> r = LectorArchivoHeroe.leer(muestra());

        // Ni el héroe, ni una faceta, ni AbilityValues/AbilityCooldown son habilidades: no traen AbilityBehavior.
        assertThat(r).doesNotContainKeys("npc_dota_hero_muestra", "huskar_bloodbath", "Facets", "AbilityValues",
                "AbilityCooldown", "DOTAHeroes");
    }

    @Test
    void leeElArchivoConFinesDeLineaCRLF() {
        Map<String, IndicadoresHabilidad> r = LectorArchivoHeroe.leer(muestra().replace("\n", "\r\n"));

        assertThat(r.get("lion_voodoo").disableDuro()).isTrue();
        assertThat(r.get("storm_spirit_ball_lightning").desactivadaPorRoot()).isTrue();
    }

    @Test
    void ignoraUnBomInicial() {
        assertThat(LectorArchivoHeroe.leer("﻿" + muestra())).containsKey("lion_voodoo");
    }

    @Test
    void unArchivoMalformadoNuncaLanzaYDevuelveLoQueAlcanzoALeer() {
        String truncado = muestra().substring(0, muestra().indexOf("\"lion_mana_drain\"") + 40);

        Map<String, IndicadoresHabilidad> r = LectorArchivoHeroe.leer(truncado);

        assertThat(r).containsKeys("lion_impale", "lion_voodoo");
    }

    @Test
    void llavesSobrantesYComillasSinCerrarNoLanzan() {
        assertThat(LectorArchivoHeroe.leer("}}}} \"a\" { \"AbilityBehavior\" \"x")).isEmpty();
        assertThat(LectorArchivoHeroe.leer("\"a\" { { { \"b\"")).isEmpty();
    }

    @Test
    void entradasVaciasONulasDevuelvenUnMapaVacio() {
        assertThat(LectorArchivoHeroe.leer(null)).isEmpty();
        assertThat(LectorArchivoHeroe.leer("")).isEmpty();
        assertThat(LectorArchivoHeroe.leer("esto no es un archivo de KeyValues")).isEmpty();
    }

    @Test
    void losComentariosConLlavesNoRompenElBloque() {
        String texto = """
                "Raiz"
                {
                  "habilidad_x" // { comentario con llave
                  {
                    "AbilityBehavior" "DOTA_ABILITY_BEHAVIOR_PASSIVE" // } otra
                    "IsBreakable" "1"
                  }
                }
                """;

        assertThat(LectorArchivoHeroe.leer(texto).get("habilidad_x").rompible()).isTrue();
    }
    // --- Archivos REALES recortados (Lion y Storm Spirit de d2vpkr, con CRLF) ---

    private static String recurso(String ruta) {
        try (InputStream in = LectorArchivoHeroeTest.class.getResourceAsStream(ruta)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void leeElArchivoRealDeLionConSusBanderas() {
        Map<String, IndicadoresHabilidad> r = LectorArchivoHeroe.leer(recurso("/juego/lion-real.txt"));

        assertThat(r).containsOnlyKeys("lion_impale", "lion_voodoo", "lion_mana_drain");
        assertThat(r.get("lion_impale").disableDuro()).isTrue();
        assertThat(r.get("lion_impale").objetivoEnemigo()).isTrue();
        assertThat(r.get("lion_voodoo").disableDuro()).isTrue();
        assertThat(r.get("lion_voodoo").desactivadaPorRoot()).isFalse();
        assertThat(r.get("lion_mana_drain").canalizada()).isTrue();
        assertThat(r.get("lion_mana_drain").disableDuro()).isFalse();
    }

    @Test
    void leeElArchivoRealDeStormSpiritConSusBanderas() {
        Map<String, IndicadoresHabilidad> r = LectorArchivoHeroe.leer(recurso("/juego/storm-spirit-real.txt"));

        assertThat(r).containsOnlyKeys("storm_spirit_electric_vortex", "storm_spirit_ball_lightning");
        assertThat(r.get("storm_spirit_ball_lightning").desactivadaPorRoot()).isTrue();
        assertThat(r.get("storm_spirit_ball_lightning").ultimate()).isTrue();
        assertThat(r.get("storm_spirit_electric_vortex").disableDuro()).isTrue();
        assertThat(r.get("storm_spirit_electric_vortex").desactivadaPorRoot()).isFalse();
    }

    @Test
    void laBarraInvertidaNoEsUnEscapeAsiQueUnValorQueTerminaEnEllaNoRompeElArchivo() {
        String texto = """
                "Raiz"
                {
                  "habilidad_x"
                  {
                    "AbilityBehavior" "DOTA_ABILITY_BEHAVIOR_PASSIVE"
                    "ruta" "C:dir\"
                    "IsBreakable" "1"
                  }
                }
                """;

        assertThat(LectorArchivoHeroe.leer(texto).get("habilidad_x").rompible()).isTrue();
    }

    @Test
    void siUnNombreDeHabilidadSeRepiteSeQuedaElPrimerBloque() {
        String texto = """
                "Raiz"
                {
                  "habilidad_x"
                  {
                    "AbilityBehavior" "DOTA_ABILITY_BEHAVIOR_PASSIVE"
                    "IsBreakable" "1"
                  }
                  "habilidad_x"
                  {
                    "AbilityBehavior" "DOTA_ABILITY_BEHAVIOR_UNIT_TARGET | DOTA_ABILITY_BEHAVIOR_NOT_LEARNABLE"
                  }
                }
                """;

        IndicadoresHabilidad x = LectorArchivoHeroe.leer(texto).get("habilidad_x");

        assertThat(x.rompible()).isTrue();
        assertThat(x.pasiva()).isTrue();
        assertThat(x.oculta()).isFalse();
    }

    private static IndicadoresHabilidad conEquipo(String comportamiento, String equipo) {
        String linea = equipo == null ? "" : "\"AbilityUnitTargetTeam\" \"" + equipo + "\"";
        String texto = "\"R\" { \"h\" { \"AbilityBehavior\" \"" + comportamiento + "\" " + linea
                + " \"SpellDispellableType\" \"SPELL_DISPELLABLE_YES_STRONG\" } }";
        return LectorArchivoHeroe.leer(texto).get("h");
    }

    @Test
    void elDisableDuroSoloApuntaAEnemigosSiElEquipoLosIncluyeOSiNoHayEquipoYNoNecesitaObjetivo() {
        assertThat(conEquipo("DOTA_ABILITY_BEHAVIOR_UNIT_TARGET", "DOTA_UNIT_TARGET_TEAM_ENEMY").objetivoEnemigo()).isTrue();
        assertThat(conEquipo("DOTA_ABILITY_BEHAVIOR_UNIT_TARGET", "DOTA_UNIT_TARGET_TEAM_FRIENDLY").objetivoEnemigo()).isFalse();
        assertThat(conEquipo("DOTA_ABILITY_BEHAVIOR_UNIT_TARGET", "DOTA_UNIT_TARGET_TEAM_BOTH").objetivoEnemigo()).isFalse();
        // Sin equipo: un área, una habilidad sin objetivo o una pasiva golpean a enemigos; una con objetivo no se sabe.
        assertThat(conEquipo("DOTA_ABILITY_BEHAVIOR_POINT | DOTA_ABILITY_BEHAVIOR_AOE", null).objetivoEnemigo()).isTrue();
        assertThat(conEquipo("DOTA_ABILITY_BEHAVIOR_NO_TARGET", null).objetivoEnemigo()).isTrue();
        assertThat(conEquipo("DOTA_ABILITY_BEHAVIOR_PASSIVE", null).objetivoEnemigo()).isTrue();
        assertThat(conEquipo("DOTA_ABILITY_BEHAVIOR_UNIT_TARGET", null).objetivoEnemigo()).isFalse();
    }
}
