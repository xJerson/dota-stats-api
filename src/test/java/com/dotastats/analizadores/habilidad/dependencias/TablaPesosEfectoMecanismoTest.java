package com.dotastats.analizadores.habilidad.dependencias;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TablaPesosEfectoMecanismoTest {

    @Test
    void contraUnEscapeElHexVaPrimeroLuegoElSilencioYDespuesElStunYElRoot() {
        double hex = TablaPesosEfectoMecanismo.multiplicador(Mecanismo.ESCAPE, Efecto.HEX);
        double silencio = TablaPesosEfectoMecanismo.multiplicador(Mecanismo.ESCAPE, Efecto.SILENCIO);
        double stun = TablaPesosEfectoMecanismo.multiplicador(Mecanismo.ESCAPE, Efecto.STUN);
        double root = TablaPesosEfectoMecanismo.multiplicador(Mecanismo.ESCAPE, Efecto.ROOT);

        assertThat(hex).isGreaterThan(silencio);
        assertThat(silencio).isGreaterThan(stun);
        assertThat(stun).isGreaterThanOrEqualTo(root);
        assertThat(root).isGreaterThan(0);
    }

    @Test
    void laInvisibilidadSeContrarrestaConRevelarYSoloUnPocoConAreas() {
        double revelar = TablaPesosEfectoMecanismo.multiplicador(Mecanismo.INVISIBILIDAD, Efecto.REVELADO);
        double area = TablaPesosEfectoMecanismo.multiplicador(Mecanismo.INVISIBILIDAD, Efecto.AREA);

        assertThat(revelar).isEqualTo(1.0);
        assertThat(area).isGreaterThan(0).isLessThan(revelar);
    }

    @Test
    void lasIlusionesSeContrarrestanConAreas() {
        assertThat(TablaPesosEfectoMecanismo.multiplicador(Mecanismo.ILUSIONES, Efecto.AREA)).isEqualTo(1.0);
    }

    @Test
    void elManaSeContrarrestaQuemandoMana() {
        assertThat(TablaPesosEfectoMecanismo.multiplicador(Mecanismo.MANA, Efecto.QUEMA_MANA)).isEqualTo(1.0);
    }

    @Test
    void lasPasivasSeContrarrestanConBreakYUnPocoMenosConDesarme() {
        double ruptura = TablaPesosEfectoMecanismo.multiplicador(Mecanismo.PASIVAS, Efecto.BREAK);
        double desarme = TablaPesosEfectoMecanismo.multiplicador(Mecanismo.PASIVAS, Efecto.DESARME);

        assertThat(ruptura).isEqualTo(1.0);
        assertThat(desarme).isGreaterThan(0).isLessThan(ruptura);
    }

    @Test
    void elAcosoADistanciaSeContrarrestaConMasAlcance() {
        assertThat(TablaPesosEfectoMecanismo.multiplicador(Mecanismo.ACOSO_RANGO, Efecto.RANGO_MAYOR)).isGreaterThan(0);
    }

    @Test
    void unaCombinacionSinRelacionPesaCero() {
        assertThat(TablaPesosEfectoMecanismo.multiplicador(Mecanismo.ESCAPE, Efecto.REVELADO)).isZero();
        assertThat(TablaPesosEfectoMecanismo.multiplicador(Mecanismo.INVISIBILIDAD, Efecto.HEX)).isZero();
        assertThat(TablaPesosEfectoMecanismo.multiplicador(Mecanismo.PASIVAS, Efecto.SILENCIO)).isZero();
    }
}
