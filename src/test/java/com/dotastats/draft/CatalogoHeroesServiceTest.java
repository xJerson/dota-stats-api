package com.dotastats.draft;

import com.dotastats.client.OpenDotaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogoHeroesServiceTest {

    private static final String CDN = "https://cdn.cloudflare.steamstatic.com";

    private OpenDotaClient openDotaClient;
    private CatalogoHeroesService servicio;

    @BeforeEach
    void preparar() {
        openDotaClient = mock(OpenDotaClient.class);
        servicio = new CatalogoHeroesService(openDotaClient, new HeroeAliasResolver(openDotaClient));
    }

    @Test
    void ordenaPorNombreYArmaLaImagenDesdeElNombreInterno() {
        when(openDotaClient.obtenerCatalogoHeroes()).thenReturn(List.of(
                heroe(11, "Shadow Fiend", "npc_dota_hero_nevermore", List.of("Carry", "Nuker")),
                heroe(1, "Anti-Mage", "npc_dota_hero_antimage", List.of("Carry", "Escape"))));

        List<HeroeInfo> heroes = servicio.listar();

        assertEquals(2, heroes.size());
        assertEquals("Anti-Mage", heroes.get(0).nombre());
        assertEquals(1, heroes.get(0).id());
        assertEquals(CDN + "/apps/dota2/images/dota_react/heroes/antimage.png", heroes.get(0).img());
        assertEquals(CDN + "/apps/dota2/images/dota_react/heroes/icons/antimage.png", heroes.get(0).icono());
        assertEquals(List.of("Carry", "Escape"), heroes.get(0).roles());
        assertEquals(List.of("am"), heroes.get(0).alias());
        assertEquals("Shadow Fiend", heroes.get(1).nombre());
        assertEquals(CDN + "/apps/dota2/images/dota_react/heroes/nevermore.png", heroes.get(1).img());
        assertEquals(List.of("sf"), heroes.get(1).alias());
    }

    @Test
    void usaElImgDelCatalogoSiExisteYQuitaElSignoFinal() {
        Map<String, Object> h = new HashMap<>(
                heroe(1, "Anti-Mage", "npc_dota_hero_antimage", List.of("Carry")));
        h.put("img", "/apps/dota2/images/dota_react/heroes/antimage.png?");
        h.put("icon", "/apps/dota2/images/dota_react/heroes/icons/custom.png?");
        when(openDotaClient.obtenerCatalogoHeroes()).thenReturn(List.of(h));

        assertEquals(CDN + "/apps/dota2/images/dota_react/heroes/antimage.png", servicio.listar().get(0).img());
        assertEquals(CDN + "/apps/dota2/images/dota_react/heroes/icons/custom.png", servicio.listar().get(0).icono());
    }

    @Test
    void sinNombreInternoNiImgLaImagenEsNula() {
        when(openDotaClient.obtenerCatalogoHeroes()).thenReturn(List.of(
                Map.of("id", 99, "localized_name", "Misterio")));

        HeroeInfo h = servicio.listar().get(0);
        assertNull(h.img());
        assertNull(h.icono());
        assertEquals(List.of(), h.roles());
        assertEquals(List.of(), h.alias());
    }

    private static Map<String, Object> heroe(int id, String nombre, String interno, List<String> roles) {
        return Map.of("id", id, "localized_name", nombre, "name", interno, "roles", roles);
    }
}
