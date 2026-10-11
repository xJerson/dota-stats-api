package com.dotastats.analizadores.habilidad;

import com.dotastats.client.OpenDotaClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidadorReglasTest {

    private final OpenDotaClient client = mock(OpenDotaClient.class);
    private final ValidadorReglas validador = new ValidadorReglas(client);

    private static ReglaHabilidad regla(String id, String habilidad, List<String> anula) {
        return new ReglaHabilidad(id, List.of("Storm Spirit"), null, "Lion", habilidad, "Hex", anula, false, false,
                "alta", "motivo");
    }

    @Test
    void unaReglaCuyaHabilidadYAnulaExistenEsOk() {
        when(client.obtenerAbilidades()).thenReturn(Map.of("lion_voodoo", Map.of(), "storm_spirit_ball_lightning", Map.of()));

        List<ResultadoValidacionRegla> r = validador.validar(
                List.of(regla("r1", "lion_voodoo", List.of("storm_spirit_ball_lightning"))));

        assertThat(r).hasSize(1);
        assertThat(r.get(0).ok()).isTrue();
        assertThat(r.get(0).problemas()).isEmpty();
    }

    @Test
    void unaHabilidadQueYaNoExisteSeReportaComoProblema() {
        when(client.obtenerAbilidades()).thenReturn(Map.of());

        List<ResultadoValidacionRegla> r = validador.validar(List.of(regla("r1", "lion_voodoo", List.of())));

        assertThat(r.get(0).ok()).isFalse();
        assertThat(r.get(0).problemas().get(0)).contains("lion_voodoo");
    }

    @Test
    void unaHabilidadAnuladaQueYaNoExisteSeReportaComoProblema() {
        when(client.obtenerAbilidades()).thenReturn(Map.of("lion_voodoo", Map.of()));

        List<ResultadoValidacionRegla> r = validador.validar(
                List.of(regla("r1", "lion_voodoo", List.of("storm_spirit_ball_lightning"))));

        assertThat(r.get(0).ok()).isFalse();
        assertThat(r.get(0).problemas().get(0)).contains("storm_spirit_ball_lightning");
    }
}
