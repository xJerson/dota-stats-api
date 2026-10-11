package com.dotastats.draft;

import com.dotastats.analizadores.FilaSugerencias;
import com.dotastats.analizadores.ResultadoFilas;
import com.dotastats.analizadores.ServicioSugerencias;
import com.dotastats.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DraftController.class)
@Import(GlobalExceptionHandler.class)
class DraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HeroeAliasResolver resolver;

    @MockBean
    private ServicioSugerencias servicioSugerencias;

    @MockBean
    private CatalogoHeroesService catalogo;

    @Test
    void heroesDevuelveElCatalogoConImagenRolesYAlias() throws Exception {
        when(catalogo.listar()).thenReturn(List.of(new HeroeInfo(11, "Shadow Fiend",
                "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/nevermore.png",
                "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/nevermore.png",
                List.of("Carry", "Nuker"), List.of("sf"))));

        mockMvc.perform(get("/api/draft/heroes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].nombre").value("Shadow Fiend"))
                .andExpect(jsonPath("$[0].img").value(
                        "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/nevermore.png"))
                .andExpect(jsonPath("$[0].icono").value(
                        "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/nevermore.png"))
                .andExpect(jsonPath("$[0].roles[1]").value("Nuker"))
                .andExpect(jsonPath("$[0].alias[0]").value("sf"));
    }

    @BeforeEach
    void servicioSinFilasPorDefecto() {
        when(servicioSugerencias.generar(anyInt(), anyMap(), anyList(), any()))
                .thenReturn(new ResultadoFilas(List.of(), List.of()));
    }

    @Test
    void rolesDevuelveLosCincoRolesNumerados() throws Exception {
        mockMvc.perform(get("/api/draft/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Carry (hard carry)"))
                .andExpect(jsonPath("$[1].nombre").value("Mid"))
                .andExpect(jsonPath("$[2].nombre").value("Offlane"))
                .andExpect(jsonPath("$[3].nombre").value("Soft support"))
                .andExpect(jsonPath("$[4].numero").value(5))
                .andExpect(jsonPath("$[4].nombre").value("Hard support"));
    }

    @Test
    void suggestResuelveSlotsYDevuelveSinFilas() throws Exception {
        when(resolver.resolver("sf")).thenReturn(ResultadoAlias.resuelto(11, "Shadow Fiend"));
        when(resolver.resolver("cm")).thenReturn(ResultadoAlias.resuelto(5, "Crystal Maiden"));

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"miRol": 2, "enemigos": {"1": "sf", "2": null}, "aliados": {"1": "cm"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miRol").value(2))
                .andExpect(jsonPath("$.resueltos.enemigos['1']").value("Shadow Fiend"))
                .andExpect(jsonPath("$.resueltos.enemigos['2']").doesNotExist())
                .andExpect(jsonPath("$.resueltos.aliados['1']").value("Crystal Maiden"))
                .andExpect(jsonPath("$.ambiguos.enemigos").isEmpty())
                .andExpect(jsonPath("$.noEncontrados.aliados").isEmpty())
                .andExpect(jsonPath("$.filas").isEmpty());
    }

    @Test
    void suggestAceptaCuerposSinEquipos() throws Exception {
        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas").isEmpty());
    }

    @Test
    void suggestInformaSlotsAmbiguosYNoEncontrados() throws Exception {
        when(resolver.resolver("shadow"))
                .thenReturn(ResultadoAlias.ambiguo(List.of("Shadow Fiend", "Shadow Shaman")));
        when(resolver.resolver("xyz")).thenReturn(ResultadoAlias.noEncontrado());

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"miRol": 3, "enemigos": {"4": "shadow"}, "aliados": {"5": "xyz"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ambiguos.enemigos['4'][0]").value("Shadow Fiend"))
                .andExpect(jsonPath("$.ambiguos.enemigos['4'][1]").value("Shadow Shaman"))
                .andExpect(jsonPath("$.noEncontrados.aliados['5']").value("xyz"))
                .andExpect(jsonPath("$.resueltos.enemigos").isEmpty())
                .andExpect(jsonPath("$.filas").isEmpty());
    }

    @Test
    void suggestDevuelve503CuandoLosDatosDelMetaNoEstanCargados() throws Exception {
        when(servicioSugerencias.generar(anyInt(), anyMap(), anyList(), any()))
                .thenThrow(new com.dotastats.exception.DatosNoCargadosException());

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("DATOS_NO_CARGADOS"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void suggestSerializaLasFilasConSusSugerenciasYConfianza() throws Exception {
        when(servicioSugerencias.generar(anyInt(), anyMap(), anyList(), any()))
                .thenReturn(new ResultadoFilas(
                        List.of(
                                new FilaSugerencias("meta", "Sugerencias por meta",
                                        List.of(new Sugerencia(1, "Anti-Mage", "52.3% en el meta", 0.51))),
                                new FilaSugerencias("habilidad", "Sugerencias por jugabilidad",
                                        List.of(new Sugerencia(26, "Lion", "Hex lo inmoviliza", 1.0, "alta")))),
                        List.of()));

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[0].id").value("meta"))
                .andExpect(jsonPath("$.filas[0].titulo").value("Sugerencias por meta"))
                .andExpect(jsonPath("$.filas[0].sugerencias[0].heroe").value("Anti-Mage"))
                .andExpect(jsonPath("$.filas[0].sugerencias[0].puntaje").value(0.51))
                .andExpect(jsonPath("$.filas[0].sugerencias[0].confianza").doesNotExist())
                .andExpect(jsonPath("$.filas[1].id").value("habilidad"))
                .andExpect(jsonPath("$.filas[1].titulo").value("Sugerencias por jugabilidad"))
                .andExpect(jsonPath("$.filas[1].sugerencias[0].heroe").value("Lion"))
                .andExpect(jsonPath("$.filas[1].sugerencias[0].confianza").value("alta"));
    }

    @Test
    void suggestIncluyeLasAdvertenciasDelServicioYVienenVaciasSiNoHay() throws Exception {
        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advertencias").isArray())
                .andExpect(jsonPath("$.advertencias").isEmpty());

        when(servicioSugerencias.generar(anyInt(), anyMap(), anyList(), any()))
                .thenReturn(new ResultadoFilas(List.of(), List.of("No se pudo consultar a Slark")));

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advertencias[0]").value("No se pudo consultar a Slark"));
    }

    // --- steamId opcional ---

    @Test
    void sinSteamIdLlamaAlServicioSinAccountId() throws Exception {
        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1}"))
                .andExpect(status().isOk());

        verify(servicioSugerencias).generar(anyInt(), anyMap(), anyList(), isNull());
    }

    @Test
    void unSteamIdEnBlancoSeTrataComoAusente() throws Exception {
        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1, \"steamId\": \"  \"}"))
                .andExpect(status().isOk());

        verify(servicioSugerencias).generar(anyInt(), anyMap(), anyList(), isNull());
    }

    @Test
    void conSteamId64LoConvierteAAccountIdYPersonaliza() throws Exception {
        when(servicioSugerencias.generar(anyInt(), anyMap(), anyList(), any()))
                .thenReturn(new ResultadoFilas(
                        List.of(new FilaSugerencias("meta", "Sugerencias por meta",
                                List.of(new Sugerencia(1, "Anti-Mage",
                                        "52.3% en el meta; tu winrate: 66% en 40 partidas", 0.51)))),
                        List.of()));

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1, \"steamId\": \"76561198065514372\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[0].sugerencias[0].motivo")
                        .value(org.hamcrest.Matchers.containsString("tu winrate")));

        verify(servicioSugerencias).generar(eq(1), anyMap(), anyList(), eq(105248644L));
    }

    @Test
    void conSteamId32TambienFunciona() throws Exception {
        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1, \"steamId\": \"105248644\"}"))
                .andExpect(status().isOk());

        verify(servicioSugerencias).generar(eq(1), anyMap(), anyList(), eq(105248644L));
    }

    @Test
    void unSteamIdInvalidoDevuelve400ConElErrorExistente() throws Exception {
        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1, \"steamId\": \"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("STEAM_ID_INVALIDO"));

        verify(servicioSugerencias, never()).generar(anyInt(), anyMap(), anyList(), any());
    }

    @Test
    void unPerfilPrivadoDevuelve422ConCodigoYMensajeAmigable() throws Exception {
        when(servicioSugerencias.generar(anyInt(), anyMap(), anyList(), any()))
                .thenThrow(new com.dotastats.exception.PerfilPrivadoException());

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1, \"steamId\": \"105248644\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PERFIL_PRIVADO"))
                .andExpect(jsonPath("$.mensaje")
                        .value(org.hamcrest.Matchers.containsString("Exponer datos públicos de partidas")));
    }

    @Test
    void suggestRechazaMiRolFueraDeRango() throws Exception {
        for (int rol : new int[]{0, 6}) {
            mockMvc.perform(post("/api/draft/suggest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"miRol\": " + rol + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SOLICITUD_INVALIDA"))
                    .andExpect(jsonPath("$.mensaje").isNotEmpty());
        }
    }

    @Test
    void suggestRechazaSlotFueraDeRango() throws Exception {
        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1, \"enemigos\": {\"6\": \"sf\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void suggestRechazaJsonMalformado() throws Exception {
        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": \"carry\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void suggestRechazaHeroeRepetidoEntreEnemigosYAliados() throws Exception {
        when(resolver.resolver(any())).thenReturn(ResultadoAlias.resuelto(11, "Shadow Fiend"));

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"miRol": 1, "enemigos": {"1": "sf"}, "aliados": {"2": "shadow fiend"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HEROE_DUPLICADO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("Shadow Fiend")));
    }

    @Test
    void suggestRechazaHeroeRepetidoDentroDelMismoEquipo() throws Exception {
        when(resolver.resolver(eq("sf"))).thenReturn(ResultadoAlias.resuelto(11, "Shadow Fiend"));

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1, \"enemigos\": {\"1\": \"sf\", \"2\": \"sf\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HEROE_DUPLICADO"));
    }

    @Test
    void elControladorPasaAlServicioLosEnemigosPorSlotYLosAliadosComoLista() throws Exception {
        when(resolver.resolver("sf")).thenReturn(ResultadoAlias.resuelto(11, "Shadow Fiend"));
        when(resolver.resolver("cm")).thenReturn(ResultadoAlias.resuelto(5, "Crystal Maiden"));
        when(resolver.resolver("am")).thenReturn(ResultadoAlias.resuelto(1, "Anti-Mage"));

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"miRol": 2, "enemigos": {"4": "sf", "2": "cm"}, "aliados": {"1": "am"}}
                                """))
                .andExpect(status().isOk());

        verify(servicioSugerencias).generar(eq(2), eq(Map.of(2, 5, 4, 11)), eq(List.of(1)), isNull());
    }

    @Test
    void conSteamIdTambienPasaLosEnemigosPorSlot() throws Exception {
        when(resolver.resolver("sf")).thenReturn(ResultadoAlias.resuelto(11, "Shadow Fiend"));

        mockMvc.perform(post("/api/draft/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"miRol\": 1, \"enemigos\": {\"3\": \"sf\"}, \"steamId\": \"105248644\"}"))
                .andExpect(status().isOk());

        verify(servicioSugerencias).generar(eq(1), eq(Map.of(3, 11)), eq(List.of()), eq(105248644L));
    }
}
