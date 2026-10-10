package com.dotastats.web;

import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.EstadoMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatosMetaService servicio;

    @Test
    void refreshIniciaLaCargaYDevuelve202() throws Exception {
        when(servicio.refrescar()).thenReturn(true);

        mockMvc.perform(post("/api/admin/refresh"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.iniciada").value(true));
    }

    @Test
    void refreshConCargaEnCursoDevuelve202SinIniciarOtra() throws Exception {
        when(servicio.refrescar()).thenReturn(false);

        mockMvc.perform(post("/api/admin/refresh"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.iniciada").value(false))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void estadoExponeParcheCacheDeMatchupsYOrigenDeLosDatos() throws Exception {
        when(servicio.estado()).thenReturn(new EstadoMeta(
                true, false, "7.41", "7.42", Instant.parse("2026-10-10T12:00:00Z"), true,
                2, List.of(11, 86), true, null));

        mockMvc.perform(get("/api/admin/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargado").value(true))
                .andExpect(jsonPath("$.cargando").value(false))
                .andExpect(jsonPath("$.parche").value("7.41"))
                .andExpect(jsonPath("$.parcheActual").value("7.42"))
                .andExpect(jsonPath("$.obsoleto").value(true))
                .andExpect(jsonPath("$.cargadoEn").value("2026-10-10T12:00:00Z"))
                .andExpect(jsonPath("$.matchupsEnCache").value(2))
                .andExpect(jsonPath("$.heroesConMatchups[0]").value(11))
                .andExpect(jsonPath("$.heroesConMatchups[1]").value(86))
                .andExpect(jsonPath("$.cacheDiscoUsada").value(true));
    }
}
