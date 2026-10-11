package com.dotastats.juego;

/**
 * Indicadores de una habilidad tomados de los archivos del juego (Valve KeyValues, ver {@link LectorArchivoHeroe}).
 * Son los que OpenDota no trae; los demás datos de la habilidad siguen saliendo de {@code /constants/abilities}.
 *
 * @param desactivadaPorRoot {@code AbilityBehavior} contiene {@code DOTA_ABILITY_BEHAVIOR_ROOT_DISABLES}: la
 *                           habilidad no se puede usar enraizado (blinks, Ball Lightning...); es lo que define un
 *                           "escape" o reposicionamiento
 * @param disableDuro        {@code SpellDispellableType} es {@code SPELL_DISPELLABLE_YES_STRONG}: solo se quita con un
 *                           dispel fuerte (aturdimientos, hexes, lazo, desmembrar...). NO distingue un stun de un hex
 * @param rompible           {@code IsBreakable == 1}: el break la anula
 * @param pasiva             {@code AbilityBehavior} contiene {@code DOTA_ABILITY_BEHAVIOR_PASSIVE}
 * @param canalizada         {@code AbilityBehavior} contiene {@code DOTA_ABILITY_BEHAVIOR_CHANNELLED}
 * @param oculta             {@code AbilityBehavior} contiene {@code HIDDEN}, {@code NOT_LEARNABLE} o {@code INNATE_UI}:
 *                           no es una habilidad jugable normal
 * @param ultimate           {@code AbilityType} es {@code ABILITY_TYPE_ULTIMATE} (las herramientas derivadas necesitan
 *                           saber si la habilidad es la ultimate para el "(desde nivel 6)" y su fiabilidad)
 * @param objetivoEnemigo    la habilidad se lanza contra enemigos: {@code AbilityUnitTargetTeam} incluye {@code ENEMY}
 *                           (ni solo {@code FRIENDLY} ni {@code BOTH}: una de ambos equipos suele proteger o mover a
 *                           un aliado), o no trae equipo y no exige objetivo (área, sin objetivo, pasiva). Sin esto un
 *                           {@code disableDuro} no es una herramienta contra el enemigo (Companion Run, Firesnap
 *                           Cookie y Planar Pocket quedan fuera)
 */
public record IndicadoresHabilidad(boolean desactivadaPorRoot, boolean disableDuro, boolean rompible, boolean pasiva,
                                   boolean canalizada, boolean oculta, boolean ultimate, boolean objetivoEnemigo) {

    /** Sin {@code objetivoEnemigo} explícito: se da por cierto (pruebas y datos escritos antes de existir el dato). */
    public IndicadoresHabilidad(boolean desactivadaPorRoot, boolean disableDuro, boolean rompible, boolean pasiva,
                                boolean canalizada, boolean oculta, boolean ultimate) {
        this(desactivadaPorRoot, disableDuro, rompible, pasiva, canalizada, oculta, ultimate, true);
    }
}
