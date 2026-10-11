package com.dotastats.analizadores;

import com.dotastats.client.Parche;
import com.dotastats.draft.PerfilJugadorService;
import com.dotastats.draft.RendimientoJugador;
import com.dotastats.draft.Sugerencia;
import com.dotastats.draft.SugerenciasProperties;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.exception.PerfilPrivadoException;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.HeroeMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ServicioSugerencias con analizadores de prueba (sin red): valida el deduplicado, el recorte y las advertencias. */
class ServicioSugerenciasTest {

    private DatosMetaService datosMetaService;
    private PerfilJugadorService perfiles;
    private DatosMeta datos;

    @BeforeEach
    void preparar() {
        datosMetaService = mock(DatosMetaService.class);
        perfiles = mock(PerfilJugadorService.class);
        Map<Integer, HeroeMeta> heroes = Map.of(
                1, new HeroeMeta(1, "Alfa", Set.of("Carry"), 1000, 530),
                2, new HeroeMeta(2, "Beta", Set.of("Carry"), 1000, 520));
        datos = new DatosMeta(heroes, new Parche(60, "7.41"), Instant.parse("2026-10-10T12:00:00Z"));
        when(datosMetaService.datosParaSugerir()).thenReturn(datos);
    }

    private static AnalizadorDraft analizador(String id, String titulo, int orden, List<Sugerencia> sugerencias) {
        return new AnalizadorDraft() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String titulo() {
                return titulo;
            }

            @Override
            public int orden() {
                return orden;
            }

            @Override
            public List<Sugerencia> sugerir(ContextoDraft ctx) {
                return sugerencias;
            }
        };
    }

    private ServicioSugerencias servicioCon(AnalizadorDraft... analizadores) {
        return new ServicioSugerencias(List.of(analizadores), datosMetaService, perfiles,
                SugerenciasProperties.porDefecto());
    }

    @Test
    void produceUnaFilaPorAnalizadorConSuIdYTitulo() {
        ServicioSugerencias servicio = servicioCon(
                analizador("meta", "Sugerencias por meta", 1, List.of(new Sugerencia(1, "Alfa", "m", 0.5))));

        ResultadoFilas r = servicio.generar(1, Map.of(), List.of(), null);

        assertThat(r.filas()).hasSize(1);
        assertThat(r.filas().get(0).id()).isEqualTo("meta");
        assertThat(r.filas().get(0).titulo()).isEqualTo("Sugerencias por meta");
        assertThat(r.filas().get(0).sugerencias()).extracting(Sugerencia::heroId).containsExactly(1);
    }

    @Test
    void respetaElOrdenDeLosAnalizadoresPorOrden() {
        AnalizadorDraft segundo = analizador("habilidad", "Sugerencias por jugabilidad", 2, List.of());
        AnalizadorDraft primero = analizador("meta", "Sugerencias por meta", 1, List.of());
        ServicioSugerencias servicio = servicioCon(segundo, primero); // inyectados en orden "equivocado"

        ResultadoFilas r = servicio.generar(1, Map.of(), List.of(), null);

        assertThat(r.filas()).extracting(FilaSugerencias::id).containsExactly("meta", "habilidad");
    }

    @Test
    void unHeroeYaMostradoPorUnaFilaDeMayorPrioridadNoSeRepiteEnLaSiguiente() {
        ServicioSugerencias servicio = servicioCon(
                analizador("meta", "Sugerencias por meta", 1, List.of(new Sugerencia(1, "Alfa", "m", 0.9))),
                analizador("habilidad", "Sugerencias por jugabilidad", 2,
                        List.of(new Sugerencia(1, "Alfa", "h", 1.0), new Sugerencia(2, "Beta", "h", 0.5))));

        ResultadoFilas r = servicio.generar(1, Map.of(), List.of(), null);

        assertThat(r.filas().get(0).sugerencias()).extracting(Sugerencia::heroId).containsExactly(1);
        assertThat(r.filas().get(1).sugerencias()).extracting(Sugerencia::heroId).containsExactly(2);
    }

    private static AnalizadorDraft conPrioridad(AnalizadorDraft base, int prioridad) {
        return new AnalizadorDraft() {
            @Override
            public String id() {
                return base.id();
            }

            @Override
            public String titulo() {
                return base.titulo();
            }

            @Override
            public int orden() {
                return base.orden();
            }

            @Override
            public int prioridadRepetidos() {
                return prioridad;
            }

            @Override
            public List<Sugerencia> sugerir(ContextoDraft ctx) {
                return base.sugerir(ctx);
            }
        };
    }

    @Test
    void unaFilaConMayorPrioridadDeRepetidosSeQuedaConElHeroeAunqueSeMuestreDespues() {
        AnalizadorDraft meta = analizador("meta", "Sugerencias por meta", 1, List.of(
                new Sugerencia(1, "Alfa", "m", 0.9), new Sugerencia(2, "Beta", "m", 0.8),
                new Sugerencia(3, "Gamma", "m", 0.7), new Sugerencia(4, "Delta", "m", 0.6)));
        AnalizadorDraft habilidad = conPrioridad(analizador("habilidad", "Sugerencias por jugabilidad", 2,
                List.of(new Sugerencia(1, "Alfa", "h", 1.0))), 0);

        ResultadoFilas r = servicioCon(meta, habilidad).generar(1, Map.of(), List.of(), null);

        // Se muestran en su orden (meta arriba), pero Alfa queda en jugabilidad y meta rellena con el siguiente
        assertThat(r.filas()).extracting(FilaSugerencias::id).containsExactly("meta", "habilidad");
        assertThat(r.filas().get(0).sugerencias()).extracting(Sugerencia::heroId).containsExactly(2, 3, 4);
        assertThat(r.filas().get(1).sugerencias()).extracting(Sugerencia::heroId).containsExactly(1);
    }

    @Test
    void lasFilasRealesDanPrioridadDeRepetidosAJugabilidad() {
        assertThat(new com.dotastats.analizadores.habilidad.AnalizadorHabilidad(null, null, null, null, null).prioridadRepetidos())
                .isLessThan(new AnalizadorMeta(null).prioridadRepetidos());
    }

    @Test
    void cadaFilaSeRecortaAlMaximoDeSugerenciasDeSiempre() {
        List<Sugerencia> muchas = List.of(
                new Sugerencia(1, "Alfa", "m", 0.9), new Sugerencia(2, "Beta", "m", 0.8),
                new Sugerencia(3, "Gamma", "m", 0.7), new Sugerencia(4, "Delta", "m", 0.6));
        ServicioSugerencias servicio = servicioCon(analizador("meta", "Sugerencias por meta", 1, muchas));

        ResultadoFilas r = servicio.generar(1, Map.of(), List.of(), null);

        assertThat(r.filas().get(0).sugerencias()).hasSize(3);
    }

    @Test
    void unaFilaSinSugerenciasQueCoincidanQuedaVaciaYNoEsError() {
        ServicioSugerencias servicio = servicioCon(
                analizador("habilidad", "Sugerencias por jugabilidad", 2, List.of()));

        ResultadoFilas r = servicio.generar(4, Map.of(), List.of(), null);

        assertThat(r.filas()).hasSize(1);
        assertThat(r.filas().get(0).sugerencias()).isEmpty();
    }

    @Test
    void recogeLasAdvertenciasQueUnAnalizadorAñadeAlContexto() {
        AnalizadorDraft conAviso = new AnalizadorDraft() {
            @Override
            public String id() {
                return "meta";
            }

            @Override
            public String titulo() {
                return "Sugerencias por meta";
            }

            @Override
            public int orden() {
                return 1;
            }

            @Override
            public List<Sugerencia> sugerir(ContextoDraft ctx) {
                ctx.advertencias().add("aviso de prueba");
                return List.of();
            }
        };
        ServicioSugerencias servicio = servicioCon(conAviso);

        ResultadoFilas r = servicio.generar(1, Map.of(), List.of(), null);

        assertThat(r.advertencias()).containsExactly("aviso de prueba");
    }

    @Test
    void sinCuentaNoConsultaElPerfil() {
        ServicioSugerencias servicio = servicioCon(analizador("meta", "Sugerencias por meta", 1, List.of()));

        servicio.generar(1, Map.of(), List.of(), null);

        verify(perfiles, never()).obtener(anyLong());
    }

    @Test
    void conCuentaElContextoLlevaElHistorialObtenido() {
        RendimientoJugador historial = new RendimientoJugador(Map.of());
        when(perfiles.obtener(555L)).thenReturn(historial);
        java.util.concurrent.atomic.AtomicReference<ContextoDraft> capturado = new java.util.concurrent.atomic.AtomicReference<>();
        AnalizadorDraft captura = new AnalizadorDraft() {
            @Override
            public String id() {
                return "meta";
            }

            @Override
            public String titulo() {
                return "Sugerencias por meta";
            }

            @Override
            public int orden() {
                return 1;
            }

            @Override
            public List<Sugerencia> sugerir(ContextoDraft ctx) {
                capturado.set(ctx);
                return List.of();
            }
        };
        ServicioSugerencias servicio = servicioCon(captura);

        servicio.generar(1, Map.of(), List.of(), 555L);

        assertThat(capturado.get().historial()).isSameAs(historial);
        assertThat(capturado.get().accountId()).isEqualTo(555L);
    }

    @Test
    void unPerfilPrivadoPropagaLaExcepcionAntesDeEjecutarLosAnalizadores() {
        when(perfiles.obtener(555L)).thenThrow(new PerfilPrivadoException());
        ServicioSugerencias servicio = servicioCon(analizador("meta", "Sugerencias por meta", 1, List.of()));

        assertThatThrownBy(() -> servicio.generar(1, Map.of(), List.of(), 555L))
                .isInstanceOf(PerfilPrivadoException.class);
    }

    @Test
    void siOpenDotaFallaAlPedirElPerfilElContextoQuedaSinHistorialYNoFalla() {
        when(perfiles.obtener(555L)).thenThrow(new OpenDotaNoDisponibleException("límite"));
        java.util.concurrent.atomic.AtomicReference<ContextoDraft> capturado = new java.util.concurrent.atomic.AtomicReference<>();
        AnalizadorDraft captura = new AnalizadorDraft() {
            @Override
            public String id() {
                return "meta";
            }

            @Override
            public String titulo() {
                return "Sugerencias por meta";
            }

            @Override
            public int orden() {
                return 1;
            }

            @Override
            public List<Sugerencia> sugerir(ContextoDraft ctx) {
                capturado.set(ctx);
                return List.of();
            }
        };
        ServicioSugerencias servicio = servicioCon(captura);

        servicio.generar(1, Map.of(), List.of(), 555L);

        assertThat(capturado.get().historial()).isNull();
    }

    @Test
    void elContextoTraeLosRivalesDirectosResueltosPorSlot() {
        java.util.concurrent.atomic.AtomicReference<ContextoDraft> capturado = new java.util.concurrent.atomic.AtomicReference<>();
        AnalizadorDraft captura = new AnalizadorDraft() {
            @Override
            public String id() {
                return "habilidad";
            }

            @Override
            public String titulo() {
                return "t";
            }

            @Override
            public int orden() {
                return 2;
            }

            @Override
            public List<Sugerencia> sugerir(ContextoDraft ctx) {
                capturado.set(ctx);
                return List.of();
            }
        };
        ServicioSugerencias servicio = servicioCon(captura);

        // posición 1: oponentes directos = slots 3 y 4
        servicio.generar(1, Map.of(3, 2, 4, 1), List.of(), null);

        assertThat(capturado.get().rivalesDirectos()).containsExactlyInAnyOrder(2, 1);
    }
}
