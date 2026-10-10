package com.dotastats.meta;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DueloTest {

    @Test
    void elComplementoInvierteLaPerspectivaDelDuelo() {
        Duelo contraElOtro = new Duelo(90, 40).complemento();

        assertThat(contraElOtro).isEqualTo(new Duelo(90, 50));
        assertThat(contraElOtro.winrate()).isEqualTo(50.0 / 90);
    }
}
