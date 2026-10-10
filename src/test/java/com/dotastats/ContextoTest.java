package com.dotastats;

import com.dotastats.draft.MotorSugerencias;
import com.dotastats.draft.MotorSugerenciasPuntaje;
import com.dotastats.draft.SugerenciasProperties;
import com.dotastats.meta.MetaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el cableado real de Spring y el binding de propiedades. La carga automática se desactiva
 * para que el test no toque la red.
 */
@SpringBootTest(properties = "dotastats.meta.cargar-al-iniciar=false")
class ContextoTest {

    @Autowired
    private MotorSugerencias motor;

    @Autowired
    private MetaProperties meta;

    @Autowired
    private SugerenciasProperties sugerencias;

    @Test
    void elMotorActivoEsElDePuntaje() {
        assertThat(motor).isInstanceOf(MotorSugerenciasPuntaje.class);
    }

    @Test
    void lasPropiedadesSeEnlazanDesdeApplicationProperties() {
        assertThat(meta.brackets()).containsExactly(6, 7);
        assertThat(meta.throttleMs()).isZero();
        assertThat(meta.cacheFile()).isEqualTo("./data/meta-cache.json");
        assertThat(sugerencias.pesoMeta()).isEqualTo(0.4);
        assertThat(sugerencias.fuerzaPrior()).isEqualTo(100);
        assertThat(sugerencias.pesoPersonal()).isEqualTo(0.25);
        assertThat(sugerencias.fuerzaPriorPersonal()).isEqualTo(20);
        assertThat(sugerencias.minPartidasPersonal()).isEqualTo(5);
        assertThat(sugerencias.roles().get(2)).isEqualTo(List.of("Nuker", "Carry"));
        assertThat(sugerencias.roles().get(5)).isEqualTo(List.of("Support"));
    }

    @Test
    void lasListasCuradasDeLas4y5SeEnlazanDesdeApplicationProperties() {
        assertThat(sugerencias.heroesPosicion().get(4)).hasSize(30).contains("Tusk", "Treant Protector");
        assertThat(sugerencias.heroesPosicion().get(5)).contains("Crystal Maiden", "Pugna")
                .doesNotContain("Dark Seer");
        assertThat(sugerencias.heroesPosicion()).doesNotContainKeys(1, 2, 3);
    }
}
