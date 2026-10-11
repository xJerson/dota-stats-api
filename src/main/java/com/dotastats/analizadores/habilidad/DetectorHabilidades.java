package com.dotastats.analizadores.habilidad;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detección por palabra clave/campo de {@link Rasgo}es (lo que tiene un enemigo) y {@link Capacidad}es (lo que
 * puede hacerle un candidato) sobre una entrada CRUDA de {@code /constants/abilities}, tal como la entrega
 * OpenDota (ver {@code docs} de la API). Es una heurística, no un oráculo: puede dar falsos positivos o
 * negativos si una descripción usa palabras distintas a las de aquí; por eso las interacciones automáticas
 * nunca pasan de confianza "media" (ver {@code AnalizadorHabilidad}) y las reglas escritas a mano siempre
 * tienen prioridad. TODOS los patrones viven en esta clase para que ajustarlos sea un solo lugar.
 * <p>
 * Campos esperados en cada entrada (pueden faltar; se tratan como ausentes, nunca como error):
 * {@code dname}, {@code behavior} (String o List&lt;String&gt;), {@code desc}, {@code target_team}
 * ({@code "Enemy"|"Friendly"|"Both"|...}), {@code dispellable} ({@code "Yes"|"No"|"Strong Dispels Only"}),
 * {@code attrib} (lista de mapas con {@code key}), {@code cd}/{@code mc} (número, cadena o lista; una lista de
 * 3 elementos es la marca de que la habilidad es la ULTIMATE, ver {@link #esUlti}).
 */
public final class DetectorHabilidades {

    private DetectorHabilidades() {
    }

    // --- Rasgos del enemigo ---
    // OJO: nada de "charg" suelto (empareja "charged image", un nuke cualquiera) ni de "Point Target + sin
    // target_team enemigo" como atajo de "se mueve solo" (empareja Tornado/Static Remnant, que son AoE
    // colocadas en un punto, no el propio héroe moviéndose): ambos daban falsos positivos verificados contra
    // datos reales. Solo cuentan frases de movimiento del PROPIO lanzador.
    private static final Pattern ESCAPE_DESC = Pattern.compile(
            "teleport|\\bblink|\\bdash|\\bleap|\\bjump|charging across|rushes?\\b|\\bsurge|"
                    + "travels? (to|across|toward)|\\bmoves?\\b|\\bphase\\b|shifts?\\b|\\bwarp|"
                    + "appears? at|reappears?|\\bvanish|arrive (at|to)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DESC_DE_DANO = Pattern.compile("damage", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVISIBLE_DESC = Pattern.compile("invisib", Pattern.CASE_INSENSITIVE);
    private static final Pattern ILUSION_DESC = Pattern.compile("illusion", Pattern.CASE_INSENSITIVE);
    private static final Pattern ILUSION_EXCLUSION = Pattern.compile(
            "true sight|destroy(s|ing)? .*illusion", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISABLE_DESC = Pattern.compile("stun|silenc|hex|root", Pattern.CASE_INSENSITIVE);
    /** Evidencia de que una habilidad SIN target_team explícito igual afecta a enemigos (ver {@link #apuntaAEnemigos}). */
    private static final Pattern ENEMY_DESC = Pattern.compile("enem", Pattern.CASE_INSENSITIVE);

    // --- Capacidades del candidato ---
    private static final Pattern HEX_DESC = Pattern.compile(
            "hex|harmless beast|transforms an enemy|turns .* into", Pattern.CASE_INSENSITIVE);
    private static final Pattern SILENCIO_DESC = Pattern.compile("silenc", Pattern.CASE_INSENSITIVE);
    private static final Pattern STUN_DESC = Pattern.compile("\\bstun", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOT_DESC = Pattern.compile("\\broot", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRUE_SIGHT_DESC = Pattern.compile("true sight", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISPEL_DESC = Pattern.compile("dispel", Pattern.CASE_INSENSITIVE);

    /** true si {@code cd} o {@code mc} es un arreglo de 3 elementos (las básicas tienen 4 niveles). */
    public static boolean esUlti(Map<String, Object> habilidad) {
        return esListaDeTres(habilidad.get("cd")) || esListaDeTres(habilidad.get("mc"));
    }

    /** {@code dname}, o la clave interna si no viene (nunca se deja sin nombre mostrable). */
    public static String nombre(Map<String, Object> habilidad, String clave) {
        Object dname = habilidad.get("dname");
        if (dname == null || String.valueOf(dname).isBlank()) {
            return clave;
        }
        return String.valueOf(dname);
    }

    public static boolean tieneRasgo(Rasgo rasgo, Map<String, Object> habilidad) {
        return switch (rasgo) {
            case ESCAPE -> esEscape(habilidad);
            case INVISIBLE -> INVISIBLE_DESC.matcher(texto(habilidad)).find();
            case ILUSIONES -> esIlusiones(habilidad);
            case CANALIZA -> behaviors(habilidad).contains("Channeled");
            case DISABLE_DISPELABLE -> esDisableDispelable(habilidad);
        };
    }

    public static boolean tieneCapacidad(Capacidad capacidad, Map<String, Object> habilidad) {
        return switch (capacidad) {
            case HEX -> apuntaAEnemigos(habilidad) && HEX_DESC.matcher(texto(habilidad)).find();
            case SILENCIO -> apuntaAEnemigos(habilidad) && SILENCIO_DESC.matcher(texto(habilidad)).find();
            case STUN -> apuntaAEnemigos(habilidad) && STUN_DESC.matcher(texto(habilidad)).find();
            case ROOT -> apuntaAEnemigos(habilidad) && ROOT_DESC.matcher(texto(habilidad)).find();
            case TRUE_SIGHT -> esTrueSight(habilidad);
            case AREA -> esArea(habilidad);
            case DISPEL_ALIADO -> esDispelAliado(habilidad);
        };
    }

    private static boolean esEscape(Map<String, Object> a) {
        if (esPasiva(a) || "friendly".equalsIgnoreCase(targetTeam(a))) {
            // Un escape es movimiento del propio lanzador; una habilidad para un ALIADO (p. ej. Alacrity,
            // "an immense surge of energy" sobre un compañero) no es eso aunque use una palabra parecida.
            return false;
        }
        String desc = texto(a);
        if (!ESCAPE_DESC.matcher(desc).find()) {
            return false;
        }
        // Un nuke sobre un enemigo que menciona una palabra de escape de paso no es un escape (p. ej.
        // "teleports a bolt of energy that deals damage"): solo se excluye si además es claramente un nuke.
        return !(apuntaAEnemigos(a) && DESC_DE_DANO.matcher(desc).find());
    }

    private static boolean esIlusiones(Map<String, Object> a) {
        String desc = texto(a);
        if (!ILUSION_DESC.matcher(desc).find() || ILUSION_EXCLUSION.matcher(desc).find()) {
            return false;
        }
        // Si la habilidad es sobre todo un escape propio (p. ej. Doppelganger, que de paso menciona
        // "illusions") se queda como ESCAPE, no como ILUSIONES: esa la cubre la que las crea de verdad
        // (Juxtapose), no la que las menciona al vuelo.
        return !esEscape(a);
    }

    private static boolean esDisableDispelable(Map<String, Object> a) {
        return apuntaAEnemigos(a) && DISABLE_DESC.matcher(texto(a)).find() && esDispelableBasico(a);
    }

    private static boolean esTrueSight(Map<String, Object> a) {
        boolean porAtributo = attribKeys(a).stream()
                .anyMatch(k -> k.toLowerCase(Locale.ROOT).contains("true_sight"));
        return porAtributo || TRUE_SIGHT_DESC.matcher(texto(a)).find();
    }

    private static boolean esArea(Map<String, Object> a) {
        if (esPasiva(a) || !apuntaAEnemigos(a)) {
            return false;
        }
        Set<String> claves = attribKeys(a).stream().map(k -> k.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        boolean tieneRadio = claves.stream().anyMatch(k -> k.contains("radius") || k.contains("aoe") || k.contains("area"));
        boolean tieneDano = claves.stream().anyMatch(k -> k.contains("damage"));
        return tieneRadio && tieneDano;
    }

    private static boolean esDispelAliado(Map<String, Object> a) {
        if (!DISPEL_DESC.matcher(texto(a)).find()) {
            return false;
        }
        String targetTeam = targetTeam(a);
        return "friendly".equalsIgnoreCase(targetTeam) || "both".equalsIgnoreCase(targetTeam);
    }

    private static boolean esPasiva(Map<String, Object> a) {
        return behaviors(a).contains("Passive");
    }

    /**
     * true si la habilidad apunta a enemigos: {@code target_team} explícito ({@code Enemy}/{@code Both}) o,
     * cuando OpenDota no lo trae (muchas AoE colocadas en un punto, como Waning Rift o Silence de Death
     * Prophet, no tienen {@code target_team}), si el texto menciona "enem[y|ies]" de forma clara.
     */
    private static boolean apuntaAEnemigos(Map<String, Object> a) {
        String tt = targetTeam(a);
        if ("enemy".equalsIgnoreCase(tt) || "both".equalsIgnoreCase(tt)) {
            return true;
        }
        if (!tt.isEmpty()) {
            return false; // target_team explícito pero no enemigo (Friendly, Self, ...): no es una suposición
        }
        return ENEMY_DESC.matcher(texto(a)).find();
    }

    private static boolean esDispelableBasico(Map<String, Object> a) {
        return "yes".equalsIgnoreCase(String.valueOf(a.get("dispellable")));
    }

    /**
     * {@code dname} + {@code desc} juntos: algunas habilidades (p. ej. Silence de Death Prophet) tienen el
     * nombre en {@code dname} pero el texto de {@code desc} no repite esa palabra ("prevents ... from casting
     * spells", sin decir "silence"); buscar en los dos evita ese hueco sin inventar nada.
     */
    private static String texto(Map<String, Object> a) {
        Object dname = a.get("dname");
        Object d = a.get("desc");
        return (dname == null ? "" : String.valueOf(dname)) + " " + (d == null ? "" : String.valueOf(d));
    }

    private static String targetTeam(Map<String, Object> a) {
        Object tt = a.get("target_team");
        return tt == null ? "" : String.valueOf(tt);
    }

    private static Set<String> behaviors(Map<String, Object> a) {
        Object b = a.get("behavior");
        if (b instanceof List<?> lista) {
            Set<String> resultado = new HashSet<>();
            lista.forEach(e -> resultado.add(String.valueOf(e)));
            return resultado;
        }
        if (b == null) {
            return Set.of();
        }
        return Set.of(String.valueOf(b));
    }

    @SuppressWarnings("unchecked")
    private static List<String> attribKeys(Map<String, Object> a) {
        Object attrib = a.get("attrib");
        if (!(attrib instanceof List<?> lista)) {
            return List.of();
        }
        return lista.stream()
                .filter(e -> e instanceof Map)
                .map(e -> (Map<String, Object>) e)
                .map(m -> m.get("key"))
                .filter(k -> k != null)
                .map(String::valueOf)
                .toList();
    }

    private static boolean esListaDeTres(Object valor) {
        return valor instanceof List<?> lista && lista.size() == 3;
    }
}
