package com.dotastats.draft;

import com.dotastats.client.OpenDotaClient;
import com.dotastats.exception.JugadorNoEncontradoException;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.exception.PerfilPrivadoException;
import com.dotastats.meta.Duelo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerfilJugadorServiceTest {

    private OpenDotaClient client;
    private PerfilJugadorService servicio;

    @BeforeEach
    void preparar() {
        client = mock(OpenDotaClient.class);
        servicio = new PerfilJugadorService(client);
    }

    /** Fila como la entrega GET /players/{id}/heroes (hay más campos, aquí solo importan estos tres). */
    static Map<String, Object> fila(int heroId, int games, int win) {
        return Map.of("hero_id", heroId, "games", games, "win", win, "last_played", 0);
    }

    @Test
    void conviertePartidasYVictoriasPorHeroeIgnorandoLosNuncaJugados() {
        when(client.obtenerHeroesJugador(42L)).thenReturn(List.of(fila(1, 40, 26), fila(2, 0, 0), fila(3, 7, 2)));

        RendimientoJugador r = servicio.obtener(42L);

        assertThat(r.de(1)).isEqualTo(new Duelo(40, 26));
        assertThat(r.de(3)).isEqualTo(new Duelo(7, 2));
        assertThat(r.de(2)).isNull();
        assertThat(r.de(99)).isNull();
    }

    @Test
    void siTodosLosHeroesTienenCeroPartidasEsPerfilPrivado() {
        // Evidencia real de OpenDota: cuenta sin datos públicos -> 200 con ~127 filas todas en 0 partidas.
        List<Map<String, Object>> filas = new ArrayList<>();
        for (int i = 1; i <= 127; i++) {
            filas.add(fila(i, 0, 0));
        }
        when(client.obtenerHeroesJugador(42L)).thenReturn(filas);

        assertThatThrownBy(() -> servicio.obtener(42L))
                .isInstanceOf(PerfilPrivadoException.class)
                .hasMessageContaining("privado")
                .hasMessageContaining("Exponer datos públicos de partidas");
    }

    @Test
    void unaListaVaciaONulaTambienEsPerfilPrivado() {
        when(client.obtenerHeroesJugador(1L)).thenReturn(List.of());
        when(client.obtenerHeroesJugador(2L)).thenReturn(null);

        assertThatThrownBy(() -> servicio.obtener(1L)).isInstanceOf(PerfilPrivadoException.class);
        assertThatThrownBy(() -> servicio.obtener(2L)).isInstanceOf(PerfilPrivadoException.class);
    }

    @Test
    void unNotFoundDeOpenDotaSeTrataComoPerfilPrivado() {
        when(client.obtenerHeroesJugador(42L)).thenThrow(new JugadorNoEncontradoException(42L));

        assertThatThrownBy(() -> servicio.obtener(42L)).isInstanceOf(PerfilPrivadoException.class);
    }

    @Test
    void siOpenDotaNoEstaDisponibleSePropagaParaQueElMotorDesactiveLaPersonalizacion() {
        when(client.obtenerHeroesJugador(42L)).thenThrow(new OpenDotaNoDisponibleException("límite"));

        assertThatThrownBy(() -> servicio.obtener(42L)).isInstanceOf(OpenDotaNoDisponibleException.class);
    }

    @Test
    void ignoraFilasSinHeroIdYToleraCamposNoNumericos() {
        when(client.obtenerHeroesJugador(42L)).thenReturn(List.of(
                Map.of("games", 5, "win", 3),
                Map.of("hero_id", 7, "games", "x", "win", 1),
                fila(8, 10, 5)));

        RendimientoJugador r = servicio.obtener(42L);

        assertThat(r.de(8)).isEqualTo(new Duelo(10, 5));
        assertThat(r.de(7)).isNull();
    }
}
