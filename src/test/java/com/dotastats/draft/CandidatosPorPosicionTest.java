package com.dotastats.draft;

import com.dotastats.client.Parche;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.HeroeMeta;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Cobertura directa del cálculo ya extraído de {@code MotorSugerenciasPuntaje}; el comportamiento de cada
 *  regla (listas curadas, línea, roles, exclusión manual) ya se cubre extensamente en
 *  {@code MotorSugerenciasPuntajeTest}, que sigue en verde tras la extracción. */
class CandidatosPorPosicionTest {

    private static DatosMeta datosCon(Map<Integer, HeroeMeta> heroes) {
        return new DatosMeta(heroes, new Parche(60, "7.41"), Instant.parse("2026-10-10T12:00:00Z"));
    }

    @Test
    void sinDatosDeLineaUsaLasEtiquetasYAvisa() {
        Map<Integer, HeroeMeta> heroes = Map.of(
                1, new HeroeMeta(1, "Alfa", Set.of("Carry"), 1000, 530),
                2, new HeroeMeta(2, "Beta", Set.of("Support"), 1000, 520));

        CandidatosPorPosicion.Resultado r = CandidatosPorPosicion.calcular(1, datosCon(heroes),
                SugerenciasProperties.porDefecto());

        assertThat(r.candidatos()).extracting(HeroeMeta::id).containsExactly(1);
        assertThat(r.sinDatosDeLinea()).isTrue();
    }

    @Test
    void unaListaCuradaNoAvisaAunqueFaltenLosDatosDeLinea() {
        Map<Integer, HeroeMeta> heroes = Map.of(
                1, new HeroeMeta(1, "Alfa", Set.of(), 1000, 530),
                2, new HeroeMeta(2, "Beta", Set.of(), 1000, 520));
        SugerenciasProperties props = new SugerenciasProperties(0.4, 0.6, 50, 100, 1.96, 0.25, 20, 5, 0.30, 30,
                1.0, 0.5, 200, 0.03, null, null, Map.of(4, List.of("Beta")));

        CandidatosPorPosicion.Resultado r = CandidatosPorPosicion.calcular(4, datosCon(heroes), props);

        assertThat(r.candidatos()).extracting(HeroeMeta::id).containsExactly(2);
        assertThat(r.sinDatosDeLinea()).isFalse();
    }

    @Test
    void noExcluyeTodaviaALosHeroesYaElegidosEnEsteDraft() {
        // Esa exclusión depende de la petición concreta, no de la posición: la aplica quien use el resultado.
        Map<Integer, HeroeMeta> heroes = Map.of(1, new HeroeMeta(1, "Alfa", Set.of("Carry"), 1000, 530));

        CandidatosPorPosicion.Resultado r = CandidatosPorPosicion.calcular(1, datosCon(heroes),
                SugerenciasProperties.porDefecto());

        assertThat(r.candidatos()).extracting(HeroeMeta::id).containsExactly(1);
    }
}
