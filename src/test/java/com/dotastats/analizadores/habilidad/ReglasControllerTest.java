package com.dotastats.analizadores.habilidad;

import com.dotastats.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReglasController.class)
@Import(GlobalExceptionHandler.class)
class ReglasControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepositorioReglas repositorio;

    @MockBean
    private ValidadorReglas validador;

    private static ReglaHabilidad regla() {
        return new ReglaHabilidad("r1", List.of("Storm Spirit"), "escape", "Lion", "lion_voodoo", "Hex",
                List.of("storm_spirit_ball_lightning"), true, false, "alta", "Hex lo inmoviliza.");
    }

    @Test
    void listarDevuelveLasReglasDelRepositorio() throws Exception {
        when(repositorio.todas()).thenReturn(List.of(regla()));

        mockMvc.perform(get("/api/reglas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("r1"))
                .andExpect(jsonPath("$[0].counter").value("Lion"))
                .andExpect(jsonPath("$[0].habilidadNombre").value("Hex"))
                .andExpect(jsonPath("$[0].confianza").value("alta"));
    }

    @Test
    void validacionDevuelveElResultadoDelValidador() throws Exception {
        when(repositorio.todas()).thenReturn(List.of(regla()));
        when(validador.validar(List.of(regla())))
                .thenReturn(List.of(new ResultadoValidacionRegla("r1", true, List.of())));

        mockMvc.perform(get("/api/reglas/validacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reglaId").value("r1"))
                .andExpect(jsonPath("$[0].ok").value(true));
    }

    @Test
    void validacionPropagaElErrorDeOpenDotaComo503() throws Exception {
        when(repositorio.todas()).thenReturn(List.of(regla()));
        when(validador.validar(List.of(regla())))
                .thenThrow(new com.dotastats.exception.OpenDotaNoDisponibleException("caído"));

        mockMvc.perform(get("/api/reglas/validacion"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("OPENDOTA_NO_DISPONIBLE"));
    }
}
