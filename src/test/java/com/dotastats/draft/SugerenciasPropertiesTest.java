package com.dotastats.draft;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SugerenciasPropertiesTest {

    private static SugerenciasProperties con(double directo, double otros) {
        return new SugerenciasProperties(0.4, 0.6, 10, 100, 1.96, 0.25, 20, 5, 0.30, 30, directo, otros, null, null, null);
    }

    @Test
    void losPesosPorDefectoSonUnoYMedio() {
        SugerenciasProperties props = SugerenciasProperties.porDefecto();

        assertThat(props.pesoOponenteDirecto()).isEqualTo(1.0);
        assertThat(props.pesoOtrosEnemigos()).isEqualTo(0.5);
    }

    @Test
    void sinConfigurarSeUsanLosValoresPorDefecto() {
        SugerenciasProperties props = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("dotastats.sugerencias", SugerenciasProperties.class);

        assertThat(props.pesoOponenteDirecto()).isEqualTo(1.0);
        assertThat(props.pesoOtrosEnemigos()).isEqualTo(0.5);
    }

    @Test
    void seEnlazanDesdeLasPropiedadesEnKebabCase() {
        SugerenciasProperties props = new Binder(new MapConfigurationPropertySource(Map.of(
                "dotastats.sugerencias.peso-oponente-directo", "2",
                "dotastats.sugerencias.peso-otros-enemigos", "0.25")))
                .bindOrCreate("dotastats.sugerencias", SugerenciasProperties.class);

        assertThat(props.pesoOponenteDirecto()).isEqualTo(2.0);
        assertThat(props.pesoOtrosEnemigos()).isEqualTo(0.25);
    }

    @Test
    void laListaDeHeroesPorPosicionSeEnlazaSeparadaPorComasYSinBlancos() {
        SugerenciasProperties props = new Binder(new MapConfigurationPropertySource(Map.of(
                "dotastats.sugerencias.heroes-posicion.4", "Tusk, Earth Spirit ,,Rubick",
                "dotastats.sugerencias.heroes-posicion.5", "")))
                .bindOrCreate("dotastats.sugerencias", SugerenciasProperties.class);

        assertThat(props.heroesPosicion().get(4)).containsExactly("Tusk", "Earth Spirit", "Rubick");
        assertThat(props.heroesPosicion()).doesNotContainKey(5);
    }

    @Test
    void sinListasConfiguradasHeroesPosicionEstaVacio() {
        assertThat(SugerenciasProperties.porDefecto().heroesPosicion()).isEmpty();
    }

    @Test
    void aceptaCeroEnUnoDeLosDosPesos() {
        assertThat(con(1.0, 0.0).pesoOtrosEnemigos()).isZero();
        assertThat(con(0.0, 1.0).pesoOponenteDirecto()).isZero();
    }

    @Test
    void rechazaPesosNegativos() {
        assertThatThrownBy(() -> con(-1.0, 0.5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("peso-oponente-directo");
        assertThatThrownBy(() -> con(1.0, -0.1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("peso-otros-enemigos");
    }

    @Test
    void rechazaQueAmbosPesosSeanCero() {
        assertThatThrownBy(() -> con(0.0, 0.0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos uno");
    }

    @Test
    void rechazaPesosNoNumericos() {
        assertThatThrownBy(() -> con(Double.NaN, 0.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
