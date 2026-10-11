package com.dotastats.analizadores;

import com.dotastats.draft.CandidatosPorPosicion;
import com.dotastats.draft.MotorSugerenciasPuntaje;
import com.dotastats.draft.PerfilJugadorService;
import com.dotastats.draft.RendimientoJugador;
import com.dotastats.draft.Sugerencia;
import com.dotastats.draft.SugerenciasProperties;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.HeroeMeta;
import com.dotastats.meta.Wilson;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orquesta las filas de sugerencias del draft: construye el {@link ContextoDraft} una sola vez por petición,
 * ejecuta cada {@link AnalizadorDraft} descubierto por Spring, muestra las filas según {@link AnalizadorDraft#orden()},
 * deja cada héroe repetido en la fila de menor {@link AnalizadorDraft#prioridadRepetidos()} (las demás rellenan con
 * su siguiente candidato) y recorta cada fila al máximo de sugerencias
 * de siempre ({@link MotorSugerenciasPuntaje#MAX_SUGERENCIAS}). Sumar una fila nueva es darle a la clase el
 * {@code @Component}: ni este servicio ni el controlador cambian.
 */
@Service
public class ServicioSugerencias {

    private final List<AnalizadorDraft> analizadores;
    private final DatosMetaService datosMetaService;
    private final PerfilJugadorService perfiles;
    private final SugerenciasProperties props;

    public ServicioSugerencias(List<AnalizadorDraft> analizadores, DatosMetaService datosMetaService,
                               PerfilJugadorService perfiles, SugerenciasProperties props) {
        this.analizadores = analizadores.stream()
                .sorted(Comparator.comparingInt(AnalizadorDraft::orden))
                .toList();
        this.datosMetaService = datosMetaService;
        this.perfiles = perfiles;
        this.props = props;
    }

    public ResultadoFilas generar(int miRol, Map<Integer, Integer> enemigosPorSlot, List<Integer> aliados,
                                  Long accountId) {
        DatosMeta datos = datosMetaService.datosParaSugerir();
        RendimientoJugador historial = null;
        if (accountId != null) {
            try {
                // Mismo historial que usará la fila de meta (y está cacheado): si OpenDota falla aquí, esa fila
                // lo vuelve a intentar y es quien añade la advertencia, para no duplicarla.
                historial = perfiles.obtener(accountId);
            } catch (OpenDotaNoDisponibleException e) {
                historial = null;
            }
        }
        ContextoDraft ctx = construirContexto(miRol, enemigosPorSlot, aliados, accountId, historial, datos);

        // Se reparten los héroes en orden de prioridad de repetidos y luego se muestran en orden de fila
        Set<Integer> yaSugeridos = new HashSet<>();
        Map<AnalizadorDraft, List<Sugerencia>> porAnalizador = new HashMap<>();
        analizadores.stream()
                .sorted(Comparator.comparingInt(AnalizadorDraft::prioridadRepetidos)
                        .thenComparingInt(AnalizadorDraft::orden))
                .forEach(a -> porAnalizador.put(a, a.sugerir(ctx).stream()
                        .filter(s -> yaSugeridos.add(s.heroId()))
                        .limit(MotorSugerenciasPuntaje.MAX_SUGERENCIAS)
                        .toList()));
        List<FilaSugerencias> filas = analizadores.stream()
                .map(a -> new FilaSugerencias(a.id(), a.titulo(), porAnalizador.get(a)))
                .toList();
        return new ResultadoFilas(filas, ctx.advertencias());
    }

    private ContextoDraft construirContexto(int miRol, Map<Integer, Integer> enemigosPorSlot, List<Integer> aliados,
                                            Long accountId, RendimientoJugador historial, DatosMeta datos) {
        Set<Integer> elegidos = new HashSet<>(enemigosPorSlot.values());
        elegidos.addAll(aliados);

        Set<Integer> slotsDirectos = MotorSugerenciasPuntaje.oponentesDirectos(miRol);
        Set<Integer> rivalesDirectos = enemigosPorSlot.entrySet().stream()
                .filter(e -> slotsDirectos.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());

        CandidatosPorPosicion.Resultado cp = CandidatosPorPosicion.calcular(miRol, datos, props);
        List<HeroeMeta> candidatos = cp.candidatos().stream()
                .filter(h -> !elegidos.contains(h.id()))
                .sorted(Comparator.comparingDouble((HeroeMeta h) -> metaPosicionDe(h, miRol, datos)).reversed()
                        .thenComparingInt(HeroeMeta::id))
                .toList();

        return new ContextoDraft(miRol, enemigosPorSlot, aliados, accountId, historial, rivalesDirectos, candidatos,
                datos, new ArrayList<>());
    }

    private double metaPosicionDe(HeroeMeta heroe, int miRol, DatosMeta datos) {
        double metaGlobal = Wilson.limiteInferior(heroe.victorias(), heroe.partidas(), props.z());
        return MotorSugerenciasPuntaje.metaPosicion(heroe, miRol, datos, metaGlobal, props);
    }
}
