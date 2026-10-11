package com.dotastats.juego;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lector del archivo de un héroe de los archivos del juego (formato KeyValues de Valve, espejo en
 * {@code dotabuff/d2vpkr}: {@code npc_dota_hero_<x>.txt}). Estructura: {@code "DOTAHeroes" { "npc_dota_hero_x" { ... } }}
 * con los bloques de habilidad anidados a profundidades distintas. Una habilidad es CUALQUIER bloque que contenga
 * DIRECTAMENTE la clave {@code AbilityBehavior}; los bloques {@code special_bonus*} (talentos) se ignoran.
 * <p>
 * Lee tokens (cadenas entre comillas, {@code {} y {@code }}), ignora comentarios {@code //} fuera de comillas, los
 * finales de línea CRLF y un BOM inicial. Es tolerante: NUNCA lanza; ante un archivo truncado o mal formado devuelve lo
 * que alcanzó a leer (las habilidades cuyo bloque llegó a cerrarse).
 */
public final class LectorArchivoHeroe {

    private static final Logger log = LoggerFactory.getLogger(LectorArchivoHeroe.class);

    private static final String CLAVE_COMPORTAMIENTO = "AbilityBehavior";
    private static final String PREFIJO_TALENTO = "special_bonus";
    private static final String COMPORTAMIENTO = "DOTA_ABILITY_BEHAVIOR_";

    private LectorArchivoHeroe() {
    }

    /** @return clave de habilidad -> sus indicadores; vacío si el texto es nulo, vacío o no se pudo leer nada */
    public static Map<String, IndicadoresHabilidad> leer(String texto) {
        Map<String, IndicadoresHabilidad> habilidades = new LinkedHashMap<>();
        if (texto == null || texto.isEmpty()) {
            return habilidades;
        }
        try {
            recorrer(texto, habilidades);
        } catch (RuntimeException e) {
            log.warn("No se pudo leer por completo el archivo de un héroe, se usa lo leído hasta ahora: {}", e.getMessage());
        }
        return habilidades;
    }

    /** Un bloque abierto: su nombre (la cadena que lo precede) y sus pares clave/valor directos. */
    private record Bloque(String nombre, Map<String, String> valores) {
    }

    private static void recorrer(String texto, Map<String, IndicadoresHabilidad> habilidades) {
        Deque<Bloque> pila = new ArrayDeque<>();
        String claveEnEspera = null;
        for (Token token : tokenizar(texto)) {
            switch (token.tipo()) {
                case CADENA -> {
                    if (claveEnEspera == null) {
                        claveEnEspera = token.texto();
                    } else {
                        if (!pila.isEmpty()) {
                            pila.peek().valores().put(claveEnEspera, token.texto());
                        }
                        claveEnEspera = null;
                    }
                }
                case ABRE -> {
                    pila.push(new Bloque(claveEnEspera, new HashMap<>()));
                    claveEnEspera = null;
                }
                case CIERRA -> {
                    claveEnEspera = null;
                    if (!pila.isEmpty()) {
                        registrarSiEsHabilidad(pila.pop(), habilidades);
                    }
                }
            }
        }
    }

    private static void registrarSiEsHabilidad(Bloque bloque, Map<String, IndicadoresHabilidad> habilidades) {
        String comportamiento = bloque.valores().get(CLAVE_COMPORTAMIENTO);
        if (comportamiento == null || bloque.nombre() == null || bloque.nombre().startsWith(PREFIJO_TALENTO)) {
            return;
        }
        Set<String> tokens = new HashSet<>();
        for (String parte : comportamiento.split("\\|")) {
            tokens.add(parte.trim());
        }
        Map<String, String> v = bloque.valores();
        // Si un nombre se repite se queda el PRIMER bloque (un segundo bloque, p. ej. una variante oculta, no lo pisa).
        habilidades.putIfAbsent(bloque.nombre(), new IndicadoresHabilidad(
                tokens.contains(COMPORTAMIENTO + "ROOT_DISABLES"),
                "SPELL_DISPELLABLE_YES_STRONG".equals(v.get("SpellDispellableType")),
                "1".equals(v.get("IsBreakable")),
                tokens.contains(COMPORTAMIENTO + "PASSIVE"),
                tokens.contains(COMPORTAMIENTO + "CHANNELLED"),
                tokens.contains(COMPORTAMIENTO + "HIDDEN") || tokens.contains(COMPORTAMIENTO + "NOT_LEARNABLE")
                        || tokens.contains(COMPORTAMIENTO + "INNATE_UI"),
                "ABILITY_TYPE_ULTIMATE".equals(v.get("AbilityType")),
                apuntaAEnemigos(v.get("AbilityUnitTargetTeam"), tokens)));
    }

    /** Ver {@link IndicadoresHabilidad#objetivoEnemigo()}. */
    private static boolean apuntaAEnemigos(String equipo, Set<String> comportamiento) {
        if (equipo == null || equipo.isBlank()) {
            return !comportamiento.contains(COMPORTAMIENTO + "UNIT_TARGET");
        }
        for (String parte : equipo.split("\\|")) {
            if ("DOTA_UNIT_TARGET_TEAM_ENEMY".equals(parte.trim())) {
                return true;
            }
        }
        return false;
    }

    private enum Tipo { CADENA, ABRE, CIERRA }

    private record Token(Tipo tipo, String texto) {
    }

    /** Cadenas entre comillas y llaves; todo lo demás (espacios, BOM, comentarios) se descarta. */
    private static List<Token> tokenizar(String s) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '{') {
                tokens.add(new Token(Tipo.ABRE, null));
                i++;
            } else if (c == '}') {
                tokens.add(new Token(Tipo.CIERRA, null));
                i++;
            } else if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                boolean cerrada = false;
                while (i < n) {
                    char d = s.charAt(i);
                    // La barra invertida NO es un escape: estos archivos no lo usan y un valor puede terminar en ella.
                    if (d == '"') {
                        cerrada = true;
                        i++;
                        break;
                    } else {
                        sb.append(d);
                        i++;
                    }
                }
                if (!cerrada) {
                    break; // comilla sin cerrar: archivo truncado, se corta aquí
                }
                tokens.add(new Token(Tipo.CADENA, sb.toString()));
            } else {
                i++;
            }
        }
        return tokens;
    }
}
