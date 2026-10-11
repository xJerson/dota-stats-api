package com.dotastats.analizadores.habilidad.dependencias;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FiabilidadHerramientaTest {

    @Test
    void unHexDeLanzamientoInstantaneoVaMasFiableQueUnStunConPuntoDeLanzamiento() {
        double hex = FiabilidadHerramienta.calcular(2.0, 0.0, 0.0, false, false);
        double stun = FiabilidadHerramienta.calcular(2.0, 0.3, 0.0, false, false);

        assertThat(hex).isGreaterThan(stun);
    }

    @Test
    void masDuracionDaMasFiabilidadHastaElTope() {
        double corta = FiabilidadHerramienta.calcular(1.0, 0.3, 0.0, false, false);
        double larga = FiabilidadHerramienta.calcular(2.0, 0.3, 0.0, false, false);
        double enElTope = FiabilidadHerramienta.calcular(FiabilidadHerramienta.DURACION_TOPE, 0.3, 0.0, false, false);
        double sobreElTope = FiabilidadHerramienta.calcular(10.0, 0.3, 0.0, false, false);

        assertThat(larga).isGreaterThan(corta);
        assertThat(enElTope).isGreaterThan(larga);
        assertThat(sobreElTope).isEqualTo(enElTope);
    }

    @Test
    void menorPuntoDeLanzamientoDaMasFiabilidad() {
        double instantaneo = FiabilidadHerramienta.calcular(1.5, 0.0, 0.0, false, false);
        double rapido = FiabilidadHerramienta.calcular(1.5, 0.25, 0.0, false, false);
        double medio = FiabilidadHerramienta.calcular(1.5, 0.45, 0.0, false, false);
        double lento = FiabilidadHerramienta.calcular(1.5, 0.7, 0.0, false, false);

        assertThat(instantaneo).isGreaterThan(rapido);
        assertThat(rapido).isGreaterThan(medio);
        assertThat(medio).isGreaterThan(lento);
    }

    @Test
    void elRetrasoDelEfectoSeSumaAlPuntoDeLanzamiento() {
        double sinRetraso = FiabilidadHerramienta.calcular(3.0, 0.3, 0.0, false, false);
        double conRetraso = FiabilidadHerramienta.calcular(3.0, 0.3, 2.6, false, false);

        assertThat(conRetraso).isLessThan(sinRetraso);
        assertThat(conRetraso).isCloseTo(FiabilidadHerramienta.calcular(3.0, 2.9, 0.0, false, false), within(1e-9));
    }

    @Test
    void atravesarInmunidadALaMagiaSuma() {
        double sin = FiabilidadHerramienta.calcular(1.0, 0.3, 0.0, false, false);
        double con = FiabilidadHerramienta.calcular(1.0, 0.3, 0.0, true, false);

        assertThat(con).isGreaterThan(sin);
    }

    @Test
    void laUltimateSePenalizaALaMitad() {
        double normal = FiabilidadHerramienta.calcular(3.0, 0.0, 0.0, true, false);
        double ulti = FiabilidadHerramienta.calcular(3.0, 0.0, 0.0, true, true);

        assertThat(ulti).isCloseTo(normal * FiabilidadHerramienta.FACTOR_ULTIMATE, within(1e-9));
        assertThat(ulti).isLessThan(normal);
    }

    @Test
    void sinDuracionUsaUnValorNeutralEntreLaMinimaYLaMaxima() {
        double sinDuracion = FiabilidadHerramienta.calcular(null, 0.3, 0.0, false, false);
        double minima = FiabilidadHerramienta.calcular(0.0, 0.3, 0.0, false, false);
        double maxima = FiabilidadHerramienta.calcular(FiabilidadHerramienta.DURACION_TOPE, 0.3, 0.0, false, false);

        assertThat(sinDuracion).isGreaterThan(minima).isLessThan(maxima);
    }

    @Test
    void elResultadoSiempreQuedaAcotado() {
        double peor = FiabilidadHerramienta.calcular(0.0, 9.0, 9.0, false, true);
        double mejor = FiabilidadHerramienta.calcular(99.0, 0.0, 0.0, true, false);

        assertThat(peor).isBetween(0.05, 1.0);
        assertThat(mejor).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void lasUltimatesNuncaSuperanALasHabilidadesNormalesIguales() {
        double normal = FiabilidadHerramienta.calcular(2.0, 0.3, 0.0, false, false);
        double ulti = FiabilidadHerramienta.calcular(2.0, 0.3, 0.0, false, true);

        assertThat(ulti).isLessThan(normal);
    }

    @Test
    void masAlcanceDeAtaqueDaMasFiabilidadConTopeYPiso() {
        double poco = FiabilidadHerramienta.deAlcance(25);
        double mas = FiabilidadHerramienta.deAlcance(100);
        double muchisimo = FiabilidadHerramienta.deAlcance(900);

        assertThat(mas).isGreaterThan(poco);
        assertThat(muchisimo).isEqualTo(1.0);
        assertThat(poco).isGreaterThanOrEqualTo(0.4);
    }
}
