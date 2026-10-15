package com.dotastats.analizadores.habilidad;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RotacionPropertiesTest {

    @Test
    void porDefectoLosSoportesRotanAlMidYLasPosicionesDeNucleoNo() {
        RotacionProperties props = new RotacionProperties(null, 0.5);

        assertThat(props.slotsDe(4)).containsExactly(2);
        assertThat(props.slotsDe(5)).containsExactly(2);
        assertThat(props.slotsDe(1)).isEmpty();
        assertThat(props.slotsDe(2)).isEmpty();
        assertThat(props.slotsDe(3)).isEmpty();
        assertThat(props.pesoRotacion()).isEqualTo(0.5);
    }

    @Test
    void unaPosicionConfiguradaReemplazaSuDefectoYLasDemasLoConservan() {
        RotacionProperties props = new RotacionProperties(Map.of(4, List.of(1, 2)), 0.5);

        assertThat(props.slotsDe(4)).containsExactly(1, 2);
        assertThat(props.slotsDe(5)).containsExactly(2);
    }

    @Test
    void unaListaVaciaApagaLaRotacionDeEsaPosicion() {
        RotacionProperties props = new RotacionProperties(Map.of(5, List.of()), 0.5);

        assertThat(props.slotsDe(5)).isEmpty();
        assertThat(props.slotsDe(4)).containsExactly(2);
    }

    @Test
    void unSlotFueraDeRangoFalla() {
        assertThatThrownBy(() -> new RotacionProperties(Map.of(4, List.of(0)), 0.5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("slots-rotacion");
        assertThatThrownBy(() -> new RotacionProperties(Map.of(4, List.of(6)), 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaPosicionFueraDeRangoFalla() {
        assertThatThrownBy(() -> new RotacionProperties(Map.of(6, List.of(2)), 0.5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("slots-rotacion");
    }

    @Test
    void unPesoNegativoFalla() {
        assertThatThrownBy(() -> new RotacionProperties(null, -0.1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("peso-rotacion");
    }

    @Test
    void pesoCeroEsValidoYApagaLaRotacion() {
        assertThat(new RotacionProperties(null, 0.0).pesoRotacion()).isZero();
    }

    @Test
    void seEnlazaDesdeLasPropiedadesDeSpring() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "dotastats.habilidad.slots-rotacion.4", "2,3",
                "dotastats.habilidad.peso-rotacion", "0.3")));

        RotacionProperties props = binder.bind("dotastats.habilidad", RotacionProperties.class).get();

        assertThat(props.slotsDe(4)).containsExactly(2, 3);
        assertThat(props.slotsDe(5)).containsExactly(2);
        assertThat(props.pesoRotacion()).isEqualTo(0.3);
    }
}
