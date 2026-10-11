package com.dotastats.analizadores.habilidad;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Detección por palabra clave / campo sobre entradas crudas de {@code /constants/abilities}. Las fixtures de
 * ESCAPE/SILENCIO/ILUSIONES son copias EXACTAS (desc/behavior/attrib/target_team) de la respuesta real de
 * OpenDota al momento de escribir esto (ver AGENTS.md §5.1); el resto son fixtures sintéticas razonables.
 * Nunca toca la red.
 */
class DetectorHabilidadesTest {

    private static Map<String, Object> habilidad(String dname, Object behavior, String desc, String targetTeam,
                                                  String dispellable, Object cd, Object mc, List<Map<String, Object>> attrib) {
        Map<String, Object> a = new HashMap<>();
        a.put("dname", dname);
        a.put("behavior", behavior);
        a.put("desc", desc);
        a.put("target_team", targetTeam);
        a.put("dispellable", dispellable);
        a.put("cd", cd);
        a.put("mc", mc);
        a.put("attrib", attrib);
        return a;
    }

    private static Map<String, Object> attrib(String key) {
        return Map.of("key", key);
    }

    // --- esUlti: un arreglo de 3 niveles (las básicas tienen 4) ---

    @Test
    void unCdDeTresNivelesEsUlti() {
        Map<String, Object> a = habilidad("Chronosphere", "Point Target", "Encierra el área.", "Enemy", "No",
                List.of("140", "120", "100"), "150", List.of());

        assertThat(DetectorHabilidades.esUlti(a)).isTrue();
    }

    @Test
    void unCdDeCuatroNivelesNoEsUlti() {
        Map<String, Object> a = habilidad("Hex", "Unit Target", "Transforms an enemy unit.", "Enemy", "No",
                List.of("25", "20", "15", "10"), "120", List.of());

        assertThat(DetectorHabilidades.esUlti(a)).isFalse();
    }

    @Test
    void unCdEscalarNoEsUlti() {
        Map<String, Object> a = habilidad("Passiva", "Passive", "", null, "No", "0", "0", List.of());

        assertThat(DetectorHabilidades.esUlti(a)).isFalse();
    }

    // --- Rasgo ESCAPE: datos REALES de /constants/abilities (sin target_team salvo que se indique) ---

    @Test
    void ballLightningEsEscape() {
        // storm_spirit_ball_lightning (real): sin target_team; "charging across the battlefield"
        Map<String, Object> a = habilidad("Ball Lightning", "Point Target",
                "Storm Spirit becomes volatile electricity, charging across the battlefield until he depletes "
                        + "his mana or reaches his target. The activation mana cost is 25+7.5% of his total "
                        + "mana pool, and the cost per 100 units traveled is 10+0.65% of his total mana pool. "
                        + "Damage is expressed in damage per 100 units traveled.",
                null, "No", "6", "30", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isTrue();
    }

    @Test
    void blinkDeQueenOfPainEsEscape() {
        // queenofpain_blink (real): sin target_team; "Short distance teleportation"
        Map<String, Object> a = habilidad("Blink", "Point Target",
                "Short distance teleportation that allows Queen of Pain to move in and out of combat.",
                null, "No", List.of("12", "10", "8", "6"), "65", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isTrue();
    }

    @Test
    void astralStepDeVoidSpiritEsEscapeAunqueApunteAEnemigos() {
        // void_spirit_astral_step (real): target_team=Enemy, "appear at the target location"; sin la palabra
        // "damage" en el desc (dice "attack"/"inflicts"), así que la exclusión de nuke no debe dispararse.
        Map<String, Object> a = habilidad("Astral Step", "Point Target",
                "Void Spirit tears a rift through the astral plane to appear at the target location, attacking "
                        + "all enemies along the path. This attack inflicts a void mark that slows for 1.25 "
                        + "seconds and then detonates.",
                "Enemy", "No", "0", "90", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isTrue();
    }

    @Test
    void activarFireRemnantDeEmberSpiritEsEscape() {
        // ember_spirit_activate_fire_remnant (real, tal cual): desc minimo, "arrive at"
        Map<String, Object> a = habilidad("Activate Fire Remnant", "Point Target", "Select the Fire Remnant to "
                + "arrive at.", null, "No", "0", List.of("100", "125", "150"), List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isTrue();
    }

    @Test
    void phaseShiftDePuckEsEscape() {
        // puck_phase_shift (real): "briefly shifts into another dimension"
        Map<String, Object> a = habilidad("Phase Shift", List.of("No Target", "Channeled"),
                "Puck briefly shifts into another dimension where it is immune from harm.", null, "No",
                List.of("8", "7.5", "7", "6.5"), "0", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isTrue();
    }

    @Test
    void etherealJauntDePuckEsEscape() {
        // puck_ethereal_jaunt (real): "Teleports Puck to a flying Illusory Orb."
        Map<String, Object> a = habilidad("Ethereal Jaunt", "No Target",
                "Teleports Puck to a flying Illusory Orb.", null, null, null, null, List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isTrue();
    }

    @Test
    void shukuchiDeWeaverEsEscape() {
        // weaver_shukuchi (real): "shifts out of visibility"; menciona "enemies" pero no "damage"
        Map<String, Object> a = habilidad("Shukuchi", List.of("No Target", "Instant Cast"),
                "Weaver shifts out of visibility, gaining max movement speed and doing harm to any enemies it "
                        + "passes through.", null, "No", List.of("15", "12", "9", "6"), "65", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isTrue();
    }

    @Test
    void skeletonWalkDeClinkzEsEscape() {
        // clinkz_wind_walk (dname real "Skeleton Walk"): "moves invisibly through units"
        Map<String, Object> a = habilidad("Skeleton Walk", List.of("No Target", "Instant Cast"),
                "Clinkz moves invisibly through units until the moment he attacks or uses items. Leaving "
                        + "Skeleton Walk creates Skeleton Archers.", null, "No", List.of("24", "21", "18"),
                List.of("80", "105", "130"), List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isTrue();
        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.INVISIBLE, a)).isTrue(); // ambos rasgos a la vez
    }

    @Test
    void doppelgangerDePhantomLancerEsEscapeYNoIlusiones() {
        // phantom_lancer_doppelwalk (real, dname "Doppelganger"): menciona "illusions" de paso, pero su efecto
        // principal es que PL "briefly vanishes" y "reappear at a random position": cuenta como ESCAPE, no
        // como ILUSIONES (esa la cubre Juxtapose, el que SÍ las crea como efecto principal).
        Map<String, Object> a = habilidad("Doppelganger", List.of("Point Target", "AOE"),
                "Phantom Lancer briefly vanishes from the battlefield. After 1 second, Phantom Lancer and any "
                        + "of his nearby illusions reappear at a random position within the targeted location, "
                        + "along with two additional doppelgangers.",
                null, "No", List.of("22", "18", "14", "10"), "70", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isTrue();
        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ILUSIONES, a)).isFalse();
    }

    @Test
    void staticRemnantDeStormSpiritNoEsEscape() {
        // storm_spirit_static_remnant (real): "charged image... walks to the target position"; sin target_team.
        // "charg" ya NO es suficiente (se quitó esa palabra suelta): no debe dar falso positivo.
        Map<String, Object> a = habilidad("Static Remnant", "No Target",
                "Creates an explosively charged image of Storm Spirit that walks to the target position and "
                        + "lasts for 12 seconds. Will detonate and deal damage if an enemy unit comes near it.",
                null, "No", "3.5", List.of("70", "80", "90", "100"), List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isFalse();
    }

    @Test
    void tornadoDeInvokerNoEsEscape() {
        // invoker_tornado (real): Point Target, sin target_team ("Hidden" en vez de target_team=Enemy); ya NO
        // cuenta por el viejo atajo "Point Target + sin target_team enemigo" (se quitó por completo).
        Map<String, Object> a = habilidad("Tornado", List.of("Point Target", "Hidden"),
                "Unleashes a fast moving tornado that picks up enemy units in its path, suspending them "
                        + "helplessly in the air shortly before allowing them to plummet to their doom. Travels "
                        + "further based on the level of Wex.",
                null, "Yes", "27", "140", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isFalse();
    }

    @Test
    void alacrityDeInvokerNoEsEscapeAunqueDigaSurge() {
        // invoker_alacrity (real): target_team=Friendly, "an immense surge of energy" (buff a un aliado, no
        // movimiento propio); "surge" solo no debe bastar cuando la habilidad es claramente para un aliado.
        Map<String, Object> a = habilidad("Alacrity", List.of("Unit Target", "Hidden"),
                "Invoker infuses an ally with an immense surge of energy, increasing their attack speed based "
                        + "on the level of Wex and their damage based on the level of Exort.",
                "Friendly", "Yes", "11", "60", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isFalse();
    }

    @Test
    void unaHabilidadPasivaNuncaEsEscapeAunqueMencioneBlink() {
        Map<String, Object> a = habilidad("Pasiva", "Passive", "Grants bonus movement speed to blink away.",
                null, "No", "0", "0", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isFalse();
    }

    @Test
    void unNukeSobreEnemigosQueMencionaUnaPalabraDeEscapeNoEsEscape() {
        Map<String, Object> a = habilidad("Nuke", "Unit Target",
                "Teleports a bolt of energy that deals heavy damage to an enemy unit.", "Enemy", "No", "10", "100",
                List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ESCAPE, a)).isFalse();
    }

    // --- Otros rasgos ---

    @Test
    void shadowWalkEsInvisible() {
        Map<String, Object> a = habilidad("Shadow Walk", "No Target",
                "Nyx Assassin becomes invisible and gains bonus movement speed.", null, "No", "20", "0", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.INVISIBLE, a)).isTrue();
    }

    @Test
    void juxtaposeEsIlusiones() {
        // phantom_lancer_juxtapose (real, Passive): su efecto PRINCIPAL es crear la ilusión; no menciona
        // ninguna palabra de escape, así que no compite con la exclusión de Doppelganger.
        Map<String, Object> a = habilidad("Juxtapose", "Passive",
                "Phantom Lancer has a chance to fracture his presence on an attack, creating an illusion of "
                        + "himself. Illusions also have a chance to fracture further.",
                null, "undefined", "undefined", "undefined", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ILUSIONES, a)).isTrue();
    }

    @Test
    void unaHabilidadQueDestruyeIlusionesNoCuentaComoRasgoDeIlusiones() {
        Map<String, Object> a = habilidad("Pulse Nova", "No Target",
                "Deals damage in an area, destroying enemy illusions caught inside.", "Enemy", "No", "30", "150",
                List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.ILUSIONES, a)).isFalse();
    }

    @Test
    void phaseShiftEsCanalizada() {
        Map<String, Object> a = habilidad("Phase Shift", List.of("No Target", "Channeled"),
                "Puck briefly shifts into another dimension where it is immune from harm.", null, "No", "16", "0",
                List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.CANALIZA, a)).isTrue();
    }

    @Test
    void unStunDispelableSobreEnemigosEsDisableDispelable() {
        Map<String, Object> a = habilidad("Impale", "Line Target",
                "Causes spikes to erupt, dealing damage and stunning enemy units.", "Enemy", "Yes", "14", "120",
                List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.DISABLE_DISPELABLE, a)).isTrue();
    }

    @Test
    void unStunQueNoSeDispeleaConBasicoNoEsDisableDispelable() {
        Map<String, Object> a = habilidad("Hex fuerte", "Unit Target", "Stuns and silences an enemy.", "Enemy",
                "Strong Dispels Only", "20", "120", List.of());

        assertThat(DetectorHabilidades.tieneRasgo(Rasgo.DISABLE_DISPELABLE, a)).isFalse();
    }

    // --- Capacidades del candidato ---

    @Test
    void voodooEsHex() {
        // lion_voodoo (real): target_team=Enemy
        Map<String, Object> a = habilidad("Hex", "Unit Target",
                "Transforms an enemy unit into a harmless beast, with all special abilities disabled.",
                "Enemy", "Strong Dispels Only", List.of("24", "20", "16", "12"), List.of("110", "140", "170", "200"),
                List.of());

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.HEX, a)).isTrue();
    }

    @Test
    void waningRiftDePuckEsSilencioAunSinTargetTeam() {
        // puck_waning_rift (real): SIN target_team, pero el desc dice "silences enemy units nearby".
        Map<String, Object> a = habilidad("Waning Rift", List.of("Point Target", "AOE"),
                "Puck teleports to the target location and releases a burst of faerie dust that deals damage "
                        + "and silences enemy units nearby.",
                null, "Yes", List.of("16", "15", "14", "13"), List.of("100", "110", "120", "130"), List.of());

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.SILENCIO, a)).isTrue();
    }

    @Test
    void silenceDeDeathProphetEsSilencioAunSinTargetTeam() {
        // death_prophet_silence (real): SIN target_team; "prevents enemy units ... from casting spells"
        Map<String, Object> a = habilidad("Silence", List.of("AOE", "Point Target"),
                "Fires a projectile that prevents enemy units in a target area from casting spells.",
                null, "Yes", List.of("15", "14", "13", "12"), List.of("80", "90", "100", "110"), List.of());

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.SILENCIO, a)).isTrue();
    }

    @Test
    void unaHabilidadSinTargetTeamYSinMencionarEnemigosNoEsCapacidad() {
        // Sin "enem" en el desc y sin target_team: no hay evidencia de que apunte a un enemigo.
        Map<String, Object> a = habilidad("Hex fantasma", "Unit Target",
                "Transforms a unit into a harmless beast.", null, "No", "25", "120", List.of());

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.HEX, a)).isFalse();
    }

    @Test
    void ancientSealEsSilencio() {
        Map<String, Object> a = habilidad("Ancient Seal", "Unit Target",
                "Skywrath Mage seals the targeted unit with an ancient rune, silencing it and causing it to "
                        + "take additional damage from spells.", "Enemy", "Yes", "14", List.of("80", "90", "100", "110"),
                List.of());

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.SILENCIO, a)).isTrue();
    }

    @Test
    void impaleEsStun() {
        Map<String, Object> a = habilidad("Impale", "Line Target", "Stuns enemy units in a line.", "Enemy", "Yes",
                "14", "120", List.of());

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.STUN, a)).isTrue();
    }

    @Test
    void unaHabilidadQueSoloMencionaRootEsRoot() {
        Map<String, Object> a = habilidad("Raíces", "Unit Target", "Roots an enemy unit in place.", "Enemy", "Yes",
                "12", "100", List.of());

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.ROOT, a)).isTrue();
    }

    @Test
    void unAtributoDeTrueSightRadiusEsTrueSight() {
        // zuus_lightning_bolt (real): attrib incluye true_sight_radius
        Map<String, Object> a = habilidad("Lightning Bolt", List.of("Unit Target", "Point Target"),
                "Calls down a bolt of lightning to strike an enemy unit, causing damage and a mini-stun. When "
                        + "cast, Lightning Bolt briefly provides unobstructed vision and True Sight around the "
                        + "target in a 600 radius.", "Enemy", "Yes", "6", List.of("120", "125", "130", "135"),
                List.of(attrib("true_sight_radius")));

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.TRUE_SIGHT, a)).isTrue();
    }

    @Test
    void unAreaConRadioYDanoEsCapacidadArea() {
        Map<String, Object> a = habilidad("Pulse Nova", "No Target", "Deals damage to enemies in an area.",
                "Enemy", "No", "30", "150", List.of(attrib("radius"), attrib("damage")));

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.AREA, a)).isTrue();
    }

    @Test
    void unAreaSinDanoNoEsCapacidadArea() {
        Map<String, Object> a = habilidad("Aura", "No Target", "Grants an aura to nearby enemies.", "Enemy", "No",
                "0", "0", List.of(attrib("radius")));

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.AREA, a)).isFalse();
    }

    @Test
    void unDispelSobreAliadosEsDispelAliado() {
        Map<String, Object> a = habilidad("Aphotic Shield", "Unit Target",
                "Dispels and shields an allied unit, including the caster.", "Friendly", "No", "13", "110",
                List.of());

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.DISPEL_ALIADO, a)).isTrue();
    }

    @Test
    void unDispelSobreEnemigosNoEsDispelAliado() {
        Map<String, Object> a = habilidad("Purga", "Unit Target", "Dispels an enemy unit of its positive buffs.",
                "Enemy", "No", "10", "100", List.of());

        assertThat(DetectorHabilidades.tieneCapacidad(Capacidad.DISPEL_ALIADO, a)).isFalse();
    }

    @Test
    void nombreUsaDnameYCaeAlaClaveSiFalta() {
        Map<String, Object> a = habilidad("Hex", "Unit Target", "", "Enemy", "No", "25", "120", List.of());
        Map<String, Object> sinDname = new HashMap<>(a);
        sinDname.remove("dname");

        assertThat(DetectorHabilidades.nombre(a, "lion_voodoo")).isEqualTo("Hex");
        assertThat(DetectorHabilidades.nombre(sinDname, "lion_voodoo")).isEqualTo("lion_voodoo");
    }
}
