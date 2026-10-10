package com.dotastats.draft;

import com.dotastats.client.OpenDotaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeroeAliasResolverTest {

    private OpenDotaClient openDotaClient;
    private HeroeAliasResolver resolver;

    @BeforeEach
    void preparar() {
        openDotaClient = mock(OpenDotaClient.class);
        when(openDotaClient.obtenerCatalogoHeroes()).thenReturn(List.of(
                heroe(1, "Anti-Mage"),
                heroe(11, "Shadow Fiend"),
                heroe(27, "Shadow Shaman"),
                heroe(5, "Crystal Maiden"),
                heroe(8, "Juggernaut"),
                heroe(25, "Lina"),
                heroe(26, "Lion"),
                heroe(14, "Pudge"),
                heroe(45, "Pugna"),
                heroe(29, "Tidehunter"),
                heroe(74, "Invoker"),
                heroe(34, "Tinker"),
                heroe(44, "Phantom Assassin"),
                heroe(12, "Phantom Lancer"),
                heroe(37, "Warlock"),
                heroe(2, "Axe"),
                heroe(16, "Sand King")));
        resolver = new HeroeAliasResolver(openDotaClient);
    }

    @Test
    void resuelveNombreExacto() {
        ResultadoAlias r = resolver.resolver("Juggernaut");
        assertEquals(ResultadoAlias.Tipo.RESUELTO, r.tipo());
        assertEquals(8, r.heroId());
        assertEquals("Juggernaut", r.nombre());
    }

    @Test
    void ignoraMayusculasEspaciosYPuntuacion() {
        assertEquals(1, resolver.resolver("anti mage").heroId());
        assertEquals(1, resolver.resolver("ANTI-MAGE").heroId());
        assertEquals(1, resolver.resolver("  antimage ").heroId());
        assertEquals("Anti-Mage", resolver.resolver("antimage").nombre());
    }

    @Test
    void ignoraAcentos() {
        ResultadoAlias r = resolver.resolver("Tidehúnter");
        assertEquals(ResultadoAlias.Tipo.RESUELTO, r.tipo());
        assertEquals(29, r.heroId());
    }

    @Test
    void resuelveAlias() {
        assertEquals(11, resolver.resolver("sf").heroId());
        assertEquals(11, resolver.resolver("SF").heroId());
        assertEquals(1, resolver.resolver("am").heroId());
        assertEquals(5, resolver.resolver("cm").heroId());
        assertEquals(16, resolver.resolver("sk").heroId());
        assertEquals(29, resolver.resolver("tide").heroId());
    }

    @Test
    void aliasDeHeroeAusenteDelCatalogoNoSeResuelve() {
        // "qop" es un alias válido pero Queen of Pain no está en el catálogo de prueba
        assertEquals(ResultadoAlias.Tipo.NO_ENCONTRADO, resolver.resolver("qop").tipo());
    }

    @Test
    void resuelvePrefijoUnico() {
        ResultadoAlias r = resolver.resolver("crystal");
        assertEquals(ResultadoAlias.Tipo.RESUELTO, r.tipo());
        assertEquals(5, r.heroId());
        assertEquals(34, resolver.resolver("tink").heroId());
    }

    @Test
    void prefijoConVariosHeroesEsAmbiguo() {
        ResultadoAlias r = resolver.resolver("shadow");
        assertEquals(ResultadoAlias.Tipo.AMBIGUO, r.tipo());
        assertEquals(List.of("Shadow Fiend", "Shadow Shaman"), r.candidatos());
    }

    @Test
    void toleraErrorTipograficoDeHastaDosCaracteres() {
        assertEquals(74, resolver.resolver("invokr").heroId());
        assertEquals(8, resolver.resolver("jugernaut").heroId());
        assertEquals(37, resolver.resolver("warlok").heroId());
    }

    @Test
    void empateEnFuzzyEsAmbiguo() {
        // "lioa" está a distancia 1 de "lina" y de "lion"
        ResultadoAlias r = resolver.resolver("lioa");
        assertEquals(ResultadoAlias.Tipo.AMBIGUO, r.tipo());
        assertEquals(List.of("Lina", "Lion"), r.candidatos());
    }

    @Test
    void entradasCortasNoUsanFuzzy() {
        // "axx" está a distancia 1 de "Axe" pero tiene menos de 4 caracteres
        assertEquals(ResultadoAlias.Tipo.NO_ENCONTRADO, resolver.resolver("axx").tipo());
    }

    @Test
    void noEncontradoCuandoNadaCoincide() {
        ResultadoAlias r = resolver.resolver("zzzzzzzz");
        assertEquals(ResultadoAlias.Tipo.NO_ENCONTRADO, r.tipo());
        assertTrue(r.candidatos().isEmpty());
    }

    @Test
    void entradaVaciaONulaNoSeEncuentra() {
        assertEquals(ResultadoAlias.Tipo.NO_ENCONTRADO, resolver.resolver("").tipo());
        assertEquals(ResultadoAlias.Tipo.NO_ENCONTRADO, resolver.resolver("  - ").tipo());
        assertEquals(ResultadoAlias.Tipo.NO_ENCONTRADO, resolver.resolver(null).tipo());
    }

    @Test
    void construyeElIndiceUnaSolaVez() {
        resolver.resolver("sf");
        resolver.resolver("lina");
        resolver.resolver("zzzzzzzz");
        verify(openDotaClient, times(1)).obtenerCatalogoHeroes();
    }

    private static Map<String, Object> heroe(int id, String nombre) {
        return Map.of("id", id, "localized_name", nombre);
    }
}
