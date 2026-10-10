package com.dotastats.draft;

import com.dotastats.client.OpenDotaClient;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Convierte lo que escribe el usuario ("sf", "anti mage", "tidehúnter", "invokr") en un héroe
 * del catálogo de OpenDota. Orden de búsqueda: nombre exacto normalizado, tabla de alias,
 * prefijo único y, por último, coincidencia aproximada (Levenshtein).
 */
@Component
public class HeroeAliasResolver {

    private static final int LARGO_MIN_FUZZY = 4;
    private static final int DISTANCIA_MAX_FUZZY = 2;

    /**
     * Abreviaturas conocidas de la comunidad -> nombre(s) localizado(s) del héroe. Se validan
     * contra el catálogo real al resolver, así que un alias cuyo héroe no exista (o cambie de
     * nombre) simplemente no resuelve. Las abreviaturas que sirven para varios héroes
     * (es, ss, dw, ...) se dejan fuera a propósito.
     */
    private static final Map<String, List<String>> ALIAS = Map.ofEntries(
            alias("sf", "Shadow Fiend"),
            alias("am", "Anti-Mage"),
            alias("pa", "Phantom Assassin"),
            alias("pl", "Phantom Lancer"),
            alias("cm", "Crystal Maiden"),
            alias("wk", "Wraith King", "Skeleton King"),
            alias("tb", "Terrorblade"),
            alias("ls", "Lifestealer"),
            alias("naix", "Lifestealer"),
            alias("qop", "Queen of Pain"),
            alias("sk", "Sand King"),
            alias("bh", "Bounty Hunter"),
            alias("bb", "Bristleback"),
            alias("np", "Nature's Prophet"),
            alias("furion", "Nature's Prophet"),
            alias("dk", "Dragon Knight"),
            alias("ds", "Dark Seer"),
            alias("wr", "Windranger"),
            alias("ww", "Winter Wyvern"),
            alias("lc", "Legion Commander"),
            alias("tide", "Tidehunter"),
            alias("ta", "Templar Assassin"),
            alias("tw", "Troll Warlord"),
            alias("ck", "Chaos Knight"),
            alias("sd", "Shadow Demon"),
            alias("sb", "Spirit Breaker"),
            alias("od", "Outworld Destroyer", "Outworld Devourer"),
            // Renombrado en el juego: el catálogo usa el nombre nuevo pero se sigue aceptando el viejo
            alias("outworld destroyer", "Outworld Devourer", "Outworld Destroyer"),
            alias("vs", "Vengeful Spirit"),
            alias("wd", "Witch Doctor"),
            alias("bs", "Bloodseeker"),
            alias("bm", "Beastmaster"),
            alias("mk", "Monkey King"),
            alias("ud", "Undying"),
            alias("dp", "Death Prophet"),
            alias("ld", "Lone Druid"),
            alias("kotl", "Keeper of the Light"),
            alias("necro", "Necrophos"),
            alias("nec", "Necrophos"),
            alias("veno", "Venomancer"),
            alias("ench", "Enchantress"),
            alias("omni", "Omniknight"),
            alias("ogre", "Ogre Magi"),
            alias("wisp", "Io"));

    private final OpenDotaClient openDotaClient;

    // El catálogo ya viene cacheado desde OpenDotaClientImpl; aquí además se memoiza el
    // índice construido (normalización incluida) para no rehacerlo en cada petición.
    private volatile Indice indice;

    public HeroeAliasResolver(OpenDotaClient openDotaClient) {
        this.openDotaClient = openDotaClient;
    }

    public ResultadoAlias resolver(String entrada) {
        String clave = normalizar(entrada);
        if (clave.isEmpty()) {
            return ResultadoAlias.noEncontrado();
        }
        Indice idx = indice();

        Heroe exacto = idx.porNombreNormalizado().get(clave);
        if (exacto != null) {
            return ResultadoAlias.resuelto(exacto.id(), exacto.nombre());
        }

        for (String nombreAlias : ALIAS.getOrDefault(clave, List.of())) {
            Heroe h = idx.porNombreNormalizado().get(normalizar(nombreAlias));
            if (h != null) {
                return ResultadoAlias.resuelto(h.id(), h.nombre());
            }
        }

        List<Heroe> porPrefijo = new ArrayList<>();
        for (Map.Entry<String, Heroe> e : idx.porNombreNormalizado().entrySet()) {
            if (e.getKey().startsWith(clave)) {
                porPrefijo.add(e.getValue());
            }
        }
        if (!porPrefijo.isEmpty()) {
            return aResultado(porPrefijo);
        }

        if (clave.length() >= LARGO_MIN_FUZZY) {
            return fuzzy(clave, idx);
        }
        return ResultadoAlias.noEncontrado();
    }

    /** Abreviaturas de la tabla integrada que apuntan a este héroe (ordenadas); vacío si no tiene. */
    public List<String> aliasesDe(String nombreHeroe) {
        String clave = normalizar(nombreHeroe);
        if (clave.isEmpty()) {
            return List.of();
        }
        return ALIAS.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(n -> normalizar(n).equals(clave)))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private ResultadoAlias fuzzy(String clave, Indice idx) {
        int mejor = Integer.MAX_VALUE;
        List<Heroe> mejores = new ArrayList<>();
        for (Map.Entry<String, Heroe> e : idx.porNombreNormalizado().entrySet()) {
            int d = distanciaLevenshtein(clave, e.getKey());
            if (d > DISTANCIA_MAX_FUZZY || d > mejor) {
                continue;
            }
            if (d < mejor) {
                mejor = d;
                mejores.clear();
            }
            mejores.add(e.getValue());
        }
        return mejores.isEmpty() ? ResultadoAlias.noEncontrado() : aResultado(mejores);
    }

    private ResultadoAlias aResultado(List<Heroe> heroes) {
        if (heroes.size() == 1) {
            Heroe h = heroes.get(0);
            return ResultadoAlias.resuelto(h.id(), h.nombre());
        }
        return ResultadoAlias.ambiguo(heroes.stream().map(Heroe::nombre).sorted().toList());
    }

    private Indice indice() {
        Indice local = indice;
        if (local == null) {
            synchronized (this) {
                local = indice;
                if (local == null) {
                    local = construirIndice();
                    indice = local;
                }
            }
        }
        return local;
    }

    private Indice construirIndice() {
        Map<String, Heroe> porNombre = new TreeMap<>();
        for (Map<String, Object> h : openDotaClient.obtenerCatalogoHeroes()) {
            int id = ((Number) h.get("id")).intValue();
            String nombre = (String) h.get("localized_name");
            porNombre.put(normalizar(nombre), new Heroe(id, nombre));
        }
        return new Indice(porNombre);
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static int distanciaLevenshtein(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + costo);
            }
            int[] tmp = anterior;
            anterior = actual;
            actual = tmp;
        }
        return anterior[b.length()];
    }

    private static Map.Entry<String, List<String>> alias(String abreviatura, String... nombres) {
        return Map.entry(normalizar(abreviatura), List.of(nombres));
    }

    private record Heroe(int id, String nombre) {
    }

    private record Indice(Map<String, Heroe> porNombreNormalizado) {
    }
}
