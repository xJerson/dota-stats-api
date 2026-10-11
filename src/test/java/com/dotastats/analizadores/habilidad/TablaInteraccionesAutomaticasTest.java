package com.dotastats.analizadores.habilidad;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TablaInteraccionesAutomaticasTest {

    @Test
    void hexEsElMejorCounterDeEscape() {
        assertThat(TablaInteraccionesAutomaticas.multiplicador(Rasgo.ESCAPE, Capacidad.HEX)).isEqualTo(1.0);
    }

    @Test
    void silencioStunYRootCuentanMenosContraEscapeQueHex() {
        double hex = TablaInteraccionesAutomaticas.multiplicador(Rasgo.ESCAPE, Capacidad.HEX);
        double silencio = TablaInteraccionesAutomaticas.multiplicador(Rasgo.ESCAPE, Capacidad.SILENCIO);
        double stun = TablaInteraccionesAutomaticas.multiplicador(Rasgo.ESCAPE, Capacidad.STUN);
        double root = TablaInteraccionesAutomaticas.multiplicador(Rasgo.ESCAPE, Capacidad.ROOT);

        assertThat(silencio).isPositive().isLessThan(hex);
        assertThat(stun).isPositive().isLessThan(hex);
        assertThat(root).isPositive().isLessThan(hex);
    }

    @Test
    void trueSightCounteraInvisible() {
        assertThat(TablaInteraccionesAutomaticas.multiplicador(Rasgo.INVISIBLE, Capacidad.TRUE_SIGHT)).isEqualTo(1.0);
    }

    @Test
    void areaCounteraIlusiones() {
        assertThat(TablaInteraccionesAutomaticas.multiplicador(Rasgo.ILUSIONES, Capacidad.AREA)).isEqualTo(1.0);
    }

    @Test
    void stunHexYSilencioInterrumpenCanalizacion() {
        assertThat(TablaInteraccionesAutomaticas.multiplicador(Rasgo.CANALIZA, Capacidad.STUN)).isPositive();
        assertThat(TablaInteraccionesAutomaticas.multiplicador(Rasgo.CANALIZA, Capacidad.HEX)).isPositive();
        assertThat(TablaInteraccionesAutomaticas.multiplicador(Rasgo.CANALIZA, Capacidad.SILENCIO)).isPositive();
    }

    @Test
    void dispelAliadoCounteraDisableDispelable() {
        assertThat(TablaInteraccionesAutomaticas.multiplicador(Rasgo.DISABLE_DISPELABLE, Capacidad.DISPEL_ALIADO))
                .isEqualTo(1.0);
    }

    @Test
    void unaCombinacionSinInteraccionValeCero() {
        assertThat(TablaInteraccionesAutomaticas.multiplicador(Rasgo.INVISIBLE, Capacidad.HEX)).isZero();
        assertThat(TablaInteraccionesAutomaticas.multiplicador(Rasgo.ILUSIONES, Capacidad.STUN)).isZero();
        assertThat(TablaInteraccionesAutomaticas.multiplicador(Rasgo.DISABLE_DISPELABLE, Capacidad.HEX)).isZero();
    }
}
