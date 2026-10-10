package com.dotastats.draft;

import com.dotastats.client.OpenDotaClient;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de héroes para la interfaz web. Usa el catálogo de OpenDota, que ya está cacheado
 * en {@link OpenDotaClient}, así que no genera llamadas extra por petición.
 */
@Service
public class CatalogoHeroesService {

    private static final String CDN = "https://cdn.cloudflare.steamstatic.com";
    private static final String RUTA_HEROES = "/apps/dota2/images/dota_react/heroes/";
    private static final String PREFIJO_INTERNO = "npc_dota_hero_";

    private final OpenDotaClient openDotaClient;
    private final HeroeAliasResolver resolver;

    public CatalogoHeroesService(OpenDotaClient openDotaClient, HeroeAliasResolver resolver) {
        this.openDotaClient = openDotaClient;
        this.resolver = resolver;
    }

    public List<HeroeInfo> listar() {
        return openDotaClient.obtenerCatalogoHeroes().stream()
                .map(this::aHeroe)
                .sorted(Comparator.comparing(h -> Normalizer.normalize(h.nombre(), Normalizer.Form.NFD).toLowerCase()))
                .toList();
    }

    private HeroeInfo aHeroe(Map<String, Object> h) {
        int id = ((Number) h.get("id")).intValue();
        String nombre = (String) h.get("localized_name");
        String corto = nombreCorto((String) h.get("name"));
        String img = absoluta((String) h.get("img"), corto == null ? null : RUTA_HEROES + corto + ".png");
        String icono = absoluta((String) h.get("icon"), corto == null ? null : RUTA_HEROES + "icons/" + corto + ".png");
        List<String> roles = h.get("roles") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        return new HeroeInfo(id, nombre, img, icono, roles, resolver.aliasesDe(nombre));
    }

    private static String nombreCorto(String interno) {
        if (interno == null || interno.isBlank()) {
            return null;
        }
        return interno.startsWith(PREFIJO_INTERNO) ? interno.substring(PREFIJO_INTERNO.length()) : interno;
    }

    private static String absoluta(String ruta, String respaldo) {
        String r = ruta == null || ruta.isBlank() ? respaldo : ruta;
        if (r == null) {
            return null;
        }
        int q = r.indexOf('?');
        if (q >= 0) {
            r = r.substring(0, q);
        }
        return r.startsWith("http") ? r : CDN + r;
    }
}
