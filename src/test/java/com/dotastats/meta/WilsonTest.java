package com.dotastats.meta;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WilsonTest {

    @Test
    void cincuentaDeCienConZ196DaCercaDe0_404() {
        assertThat(Wilson.limiteInferior(50, 100, 1.96)).isCloseTo(0.4038, within(0.001));
    }

    @Test
    void sinPartidasDaCero() {
        assertThat(Wilson.limiteInferior(0, 0, 1.96)).isZero();
    }

    @Test
    void mismaTasaConMasMuestraDaLimiteMasAlto() {
        assertThat(Wilson.limiteInferior(500, 1000, 1.96))
                .isGreaterThan(Wilson.limiteInferior(50, 100, 1.96));
    }

    @Test
    void siempreQuedaPorDebajoDeLaTasaObservada() {
        assertThat(Wilson.limiteInferior(70, 100, 1.96)).isLessThan(0.70);
    }

    @Test
    void victoriasTotalesNoSuperanUno() {
        assertThat(Wilson.limiteInferior(10, 10, 1.96)).isBetween(0.0, 1.0);
    }
}
